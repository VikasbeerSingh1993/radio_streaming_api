package com.radiostreaming.api.saas.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.radiostreaming.api.saas.model.SaasUsageEventDocument;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class SaasUsageEventJdbcRepository implements SaasUsageEventRepository {

    private static final String COLUMNS = """
            id, user_id, api_key_id, operation, credits_charged, overage, status, metadata_json, created_at
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RowMapper<SaasUsageEventDocument> rowMapper;

    public SaasUsageEventJdbcRepository(
            @Qualifier("saasJdbcTemplate") JdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.rowMapper = (rs, rowNum) -> {
            SaasUsageEventDocument e = new SaasUsageEventDocument();
            e.setId(rs.getString("id"));
            e.setUserId(rs.getString("user_id"));
            e.setApiKeyId(rs.getString("api_key_id"));
            e.setOperation(rs.getString("operation"));
            e.setCreditsCharged(rs.getInt("credits_charged"));
            e.setOverage(SaasJdbcSupport.toBoolean(rs.getInt("overage")));
            e.setStatus(rs.getString("status"));
            e.setMetadata(SaasJdbcSupport.readObjectMap(objectMapper, rs.getString("metadata_json")));
            e.setCreatedAt(SaasJdbcSupport.toInstant(rs.getTimestamp("created_at")));
            return e;
        };
    }

    @Override
    public Optional<SaasUsageEventDocument> findById(String id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT " + COLUMNS + " FROM saas_usage_events WHERE id = ?",
                    rowMapper,
                    id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public SaasUsageEventDocument save(SaasUsageEventDocument event) {
        event.setId(SaasJdbcSupport.newIdIfBlank(event.getId()));
        String metadataJson = SaasJdbcSupport.writeJson(objectMapper, event.getMetadata());
        boolean exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM saas_usage_events WHERE id = ?",
                Integer.class,
                event.getId()) > 0;
        if (exists) {
            jdbc.update("""
                    UPDATE saas_usage_events SET
                      user_id = ?, api_key_id = ?, operation = ?, credits_charged = ?, overage = ?,
                      status = ?, metadata_json = ?, created_at = ?
                    WHERE id = ?
                    """,
                    event.getUserId(),
                    event.getApiKeyId(),
                    event.getOperation(),
                    event.getCreditsCharged(),
                    SaasJdbcSupport.toTinyInt(event.isOverage()),
                    event.getStatus(),
                    metadataJson,
                    SaasJdbcSupport.toTimestamp(event.getCreatedAt()),
                    event.getId());
        } else {
            jdbc.update("""
                    INSERT INTO saas_usage_events (
                      id, user_id, api_key_id, operation, credits_charged, overage, status, metadata_json, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    event.getId(),
                    event.getUserId(),
                    event.getApiKeyId(),
                    event.getOperation(),
                    event.getCreditsCharged(),
                    SaasJdbcSupport.toTinyInt(event.isOverage()),
                    event.getStatus(),
                    metadataJson,
                    SaasJdbcSupport.toTimestamp(event.getCreatedAt()));
        }
        return event;
    }

    @Override
    public List<SaasUsageEventDocument> findAll() {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM saas_usage_events ORDER BY created_at DESC",
                rowMapper);
    }

    @Override
    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM saas_usage_events", Long.class);
        return n == null ? 0L : n;
    }

    @Override
    public void delete(SaasUsageEventDocument event) {
        if (event != null && event.getId() != null) {
            jdbc.update("DELETE FROM saas_usage_events WHERE id = ?", event.getId());
        }
    }

    @Override
    public Page<SaasUsageEventDocument> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable) {
        long total = countByUserId(userId);
        List<SaasUsageEventDocument> content = jdbc.query(
                "SELECT " + COLUMNS + " FROM saas_usage_events WHERE user_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                rowMapper,
                userId,
                pageable.getPageSize(),
                pageable.getOffset());
        return new PageImpl<>(content, pageable, total);
    }

    @Override
    public long countByUserId(String userId) {
        Long n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM saas_usage_events WHERE user_id = ?",
                Long.class,
                userId);
        return n == null ? 0L : n;
    }

    @Override
    public long countByUserIdAndOperationSince(String userId, String operation, java.time.Instant since) {
        Long n = jdbc.queryForObject(
                """
                SELECT COUNT(*) FROM saas_usage_events
                WHERE user_id = ? AND operation = ? AND created_at >= ?
                """,
                Long.class,
                userId,
                operation,
                SaasJdbcSupport.toTimestamp(since));
        return n == null ? 0L : n;
    }
}
