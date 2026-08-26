package com.radiostreaming.api.service;

import com.radiostreaming.api.dto.GurbaniSearchHit;
import com.radiostreaming.api.dto.GurbaniSearchPage;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gurbani search against {@code bani_search.search_documents} (BaniDB schema).
 * Modes: {@code word} (FULLTEXT) and {@code prefix} (starting-word / first-akhar style).
 */
@Service
public class GurbaniSearchService {

    private static final Logger log = LoggerFactory.getLogger(GurbaniSearchService.class);
    private static final int MAX_SIZE = 50;

    private final JdbcTemplate jdbc;
    private final DataSource dataSource;

    public GurbaniSearchService(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.dataSource = dataSource;
    }

    public boolean isAvailable() {
        if (!hasLikelyCredentials()) {
            return false;
        }
        try {
            Integer one = jdbc.queryForObject("SELECT 1", Integer.class);
            return one != null && one == 1;
        } catch (Exception ex) {
            return false;
        }
    }

    public List<Map<String, Object>> listSources() {
        if (!hasLikelyCredentials()) {
            return staticSources();
        }
        try {
            return jdbc.query(
                    "SELECT code, english, gurmukhi, unicode FROM sources ORDER BY FIELD(code,'G','D','B','S','A'), code",
                    (rs, rowNum) -> {
                        Map<String, Object> row = new LinkedHashMap<>();
                        row.put("code", rs.getString("code"));
                        row.put("english", rs.getString("english"));
                        row.put("gurmukhi", rs.getString("gurmukhi"));
                        row.put("unicode", rs.getString("unicode"));
                        return row;
                    });
        } catch (DataAccessException ex) {
            log.warn("sources table unavailable; returning static codes", ex);
            return staticSources();
        }
    }

    public GurbaniSearchPage search(String q, String mode, String source, int page, int size) {
        String query = q == null ? "" : q.trim();
        String searchMode = normalizeMode(mode);
        String sourceCode = normalizeSource(source);
        int safePage = Math.max(0, page);
        int safeSize = Math.min(MAX_SIZE, Math.max(1, size));
        int offset = safePage * safeSize;

        GurbaniSearchPage result = new GurbaniSearchPage();
        result.setPage(safePage);
        result.setSize(safeSize);

        if (query.isEmpty()) {
            result.setAvailable(isAvailable());
            result.setTotal(0);
            result.setMessage("Enter a search query");
            return result;
        }

        if (!hasLikelyCredentials()) {
            result.setAvailable(false);
            result.setItems(List.of());
            result.setTotal(0);
            result.setMessage("Gurbani database is not available. Configure MYSQL credentials and restart.");
            return result;
        }

        try {
            if ("prefix".equals(searchMode)) {
                return prefixSearch(query, sourceCode, safePage, safeSize, offset, result);
            }
            return wordSearch(query, sourceCode, safePage, safeSize, offset, result);
        } catch (DataAccessException ex) {
            log.warn("Gurbani search failed (MySQL may be offline): {}", ex.getMessage());
            result.setAvailable(false);
            result.setItems(List.of());
            result.setTotal(0);
            result.setMessage("Gurbani database is not available. Configure MYSQL credentials and restart.");
            return result;
        }
    }

    public Map<String, Object> getAng(int ang, String source) {
        String sourceCode = normalizeSource(source);
        if ("all".equals(sourceCode) || sourceCode.isBlank()) {
            sourceCode = "G";
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ang", ang);
        body.put("source", sourceCode);
        try {
            List<GurbaniSearchHit> lines = jdbc.query(
                    """
                    SELECT verse_id, source_code, shabad_id, page_no, line_no, ang,
                           gurmukhi, unicode, translit_english, english_bdb, english_ms, english_ssk,
                           writer_english, raag_english
                    FROM search_documents
                    WHERE source_code = ? AND (ang = ? OR page_no = ?)
                    ORDER BY page_no, line_no, verse_id
                    """,
                    hitMapper("browse"),
                    sourceCode, ang, ang);
            body.put("available", true);
            body.put("lines", lines);
            body.put("total", lines.size());
            if (!lines.isEmpty()) {
                GurbaniSearchHit first = lines.get(0);
                body.put("shabadId", first.getShabadId());
                body.put("writer", first.getWriter());
                body.put("raag", first.getRaag());
                body.put("title", first.getUnicode() != null ? first.getUnicode() : first.getGurmukhi());
            }
            return body;
        } catch (DataAccessException ex) {
            log.warn("Ang lookup failed: {}", ex.getMessage());
            body.put("available", false);
            body.put("lines", List.of());
            body.put("total", 0);
            body.put("message", "Gurbani database is not available");
            return body;
        }
    }

    public Map<String, Object> getShabad(long shabadId) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", shabadId);
        try {
            List<GurbaniSearchHit> lines = jdbc.query(
                    """
                    SELECT verse_id, source_code, shabad_id, page_no, line_no, ang,
                           gurmukhi, unicode, translit_english, english_bdb, english_ms, english_ssk,
                           writer_english, raag_english
                    FROM search_documents
                    WHERE shabad_id = ?
                    ORDER BY page_no, line_no, verse_id
                    """,
                    hitMapper("shabad"),
                    shabadId);
            body.put("available", true);
            body.put("lines", lines);
            body.put("total", lines.size());
            if (!lines.isEmpty()) {
                GurbaniSearchHit first = lines.get(0);
                body.put("sourceCode", first.getSourceCode());
                body.put("ang", first.getAng() != null ? first.getAng() : first.getPageNo());
                body.put("writer", first.getWriter());
                body.put("raag", first.getRaag());
                body.put("title", first.getUnicode() != null ? first.getUnicode() : first.getGurmukhi());
            }
            return body;
        } catch (DataAccessException ex) {
            log.warn("Shabad lookup failed: {}", ex.getMessage());
            body.put("available", false);
            body.put("lines", List.of());
            body.put("total", 0);
            body.put("message", "Gurbani database is not available");
            return body;
        }
    }

    private GurbaniSearchPage wordSearch(
            String query, String sourceCode, int page, int size, int offset, GurbaniSearchPage result) {
        String sourceClause = sourceFilterSql(sourceCode);
        Long total = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM search_documents
                WHERE MATCH(search_blob) AGAINST (? IN NATURAL LANGUAGE MODE)
                """ + sourceClause,
                Long.class,
                bindSource(query, sourceCode));

        List<GurbaniSearchHit> items = jdbc.query(
                """
                SELECT verse_id, source_code, shabad_id, page_no, line_no, ang,
                       gurmukhi, unicode, translit_english, english_bdb, english_ms, english_ssk,
                       writer_english, raag_english,
                       MATCH(search_blob) AGAINST (? IN NATURAL LANGUAGE MODE) AS score
                FROM search_documents
                WHERE MATCH(search_blob) AGAINST (? IN NATURAL LANGUAGE MODE)
                """ + sourceClause + """
                ORDER BY score DESC, page_no, line_no
                LIMIT ? OFFSET ?
                """,
                hitMapper("word"),
                bindSourceWithLimit(query, sourceCode, size, offset));

        result.setAvailable(true);
        result.setTotal(total == null ? 0 : total);
        result.setItems(items);
        return result;
    }

    private GurbaniSearchPage prefixSearch(
            String query, String sourceCode, int page, int size, int offset, GurbaniSearchPage result) {
        String like = sanitizeLikePrefix(query) + "%";
        String sourceClause = sourceFilterSql(sourceCode);
        // Starting-word / first-akhar style: line begins with query, or a word begins with query.
        String where = """
                WHERE (
                  unicode LIKE ?
                  OR gurmukhi LIKE ?
                  OR unicode LIKE CONCAT('% ', ?)
                  OR gurmukhi LIKE CONCAT('% ', ?)
                )
                """ + sourceClause;

        List<Object> countArgs = new ArrayList<>();
        countArgs.add(like);
        countArgs.add(like);
        countArgs.add(like);
        countArgs.add(like);
        if (!"all".equals(sourceCode) && !sourceCode.isBlank()) {
            countArgs.add(sourceCode);
        }

        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM search_documents " + where,
                Long.class,
                countArgs.toArray());

        List<Object> listArgs = new ArrayList<>(countArgs);
        listArgs.add(size);
        listArgs.add(offset);

        List<GurbaniSearchHit> items = jdbc.query(
                """
                SELECT verse_id, source_code, shabad_id, page_no, line_no, ang,
                       gurmukhi, unicode, translit_english, english_bdb, english_ms, english_ssk,
                       writer_english, raag_english, NULL AS score
                FROM search_documents
                """ + where + """
                ORDER BY page_no, line_no, verse_id
                LIMIT ? OFFSET ?
                """,
                hitMapper("prefix"),
                listArgs.toArray());

        result.setAvailable(true);
        result.setTotal(total == null ? 0 : total);
        result.setItems(items);
        return result;
    }

    private static String sourceFilterSql(String sourceCode) {
        if ("all".equals(sourceCode) || sourceCode.isBlank()) {
            return "";
        }
        return " AND source_code = ? ";
    }

    private static Object[] bindSource(String query, String sourceCode) {
        if ("all".equals(sourceCode) || sourceCode.isBlank()) {
            return new Object[]{query};
        }
        return new Object[]{query, sourceCode};
    }

    private static Object[] bindSourceWithLimit(String query, String sourceCode, int size, int offset) {
        if ("all".equals(sourceCode) || sourceCode.isBlank()) {
            return new Object[]{query, query, size, offset};
        }
        return new Object[]{query, query, sourceCode, size, offset};
    }

    private static String normalizeMode(String mode) {
        if (mode == null || mode.isBlank()) {
            return "word";
        }
        String m = mode.trim().toLowerCase(Locale.ROOT);
        return "prefix".equals(m) || "start".equals(m) || "starting".equals(m) ? "prefix" : "word";
    }

    private static String normalizeSource(String source) {
        if (source == null || source.isBlank() || "all".equalsIgnoreCase(source)) {
            return "all";
        }
        return source.trim().toUpperCase(Locale.ROOT).substring(0, 1);
    }

    private boolean hasLikelyCredentials() {
        if (dataSource instanceof HikariDataSource hikari) {
            String user = hikari.getUsername();
            return user != null && !user.isBlank();
        }
        return true;
    }

    private static String sanitizeLikePrefix(String value) {
        return value.replace("%", "").replace("_", "").trim();
    }

    private static RowMapper<GurbaniSearchHit> hitMapper(String matchMode) {
        return (rs, rowNum) -> mapHit(rs, matchMode);
    }

    private static GurbaniSearchHit mapHit(ResultSet rs, String matchMode) throws SQLException {
        GurbaniSearchHit hit = new GurbaniSearchHit();
        hit.setVerseId(rs.getLong("verse_id"));
        hit.setSourceCode(rs.getString("source_code"));
        long shabad = rs.getLong("shabad_id");
        hit.setShabadId(rs.wasNull() ? null : shabad);
        hit.setPageNo(rs.getObject("page_no") == null ? null : rs.getInt("page_no"));
        hit.setLineNo(rs.getObject("line_no") == null ? null : rs.getInt("line_no"));
        hit.setAng(rs.getObject("ang") == null ? hit.getPageNo() : rs.getInt("ang"));
        hit.setGurmukhi(rs.getString("gurmukhi"));
        hit.setUnicode(rs.getString("unicode"));
        hit.setTransliteration(rs.getString("translit_english"));
        String translation = firstNonBlank(
                rs.getString("english_bdb"),
                rs.getString("english_ms"),
                rs.getString("english_ssk"));
        hit.setTranslation(translation);
        hit.setWriter(rs.getString("writer_english"));
        hit.setRaag(rs.getString("raag_english"));
        try {
            double score = rs.getDouble("score");
            if (!rs.wasNull()) {
                hit.setScore(score);
            }
        } catch (SQLException ignored) {
            // score column optional
        }
        hit.setMatchMode(matchMode);
        return hit;
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return null;
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private static List<Map<String, Object>> staticSources() {
        List<Map<String, Object>> list = new ArrayList<>();
        list.add(Map.of("code", "G", "english", "Sri Guru Granth Sahib Ji"));
        list.add(Map.of("code", "D", "english", "Dasam Granth"));
        list.add(Map.of("code", "B", "english", "Bhai Gurdas Ji Vaaran"));
        list.add(Map.of("code", "S", "english", "Bhai Gurdas Singh Ji Vaaran"));
        list.add(Map.of("code", "A", "english", "Amrit Keertan"));
        return list;
    }
}
