package com.radiostreaming.api.cms;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SiteCmsService {

    private final JdbcTemplate jdbc;

    public SiteCmsService(@Qualifier("saasJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> publicBundle() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("settings", settingsMap());
        body.put("pages", pagesMap());
        body.put("media", listMedia(true));
        return body;
    }

    public Map<String, String> settingsMap() {
        Map<String, String> map = new LinkedHashMap<>();
        jdbc.query("SELECT setting_key, setting_value FROM site_settings", rs -> {
            map.put(rs.getString("setting_key"), rs.getString("setting_value"));
        });
        return map;
    }

    public Map<String, Map<String, Object>> pagesMap() {
        Map<String, Map<String, Object>> map = new LinkedHashMap<>();
        jdbc.query("SELECT page_key, title, subtitle, body_html, hero_image_url FROM site_pages", rs -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("key", rs.getString("page_key"));
            row.put("title", rs.getString("title"));
            row.put("subtitle", rs.getString("subtitle"));
            row.put("bodyHtml", rs.getString("body_html"));
            row.put("heroImageUrl", rs.getString("hero_image_url"));
            map.put(rs.getString("page_key"), row);
        });
        return map;
    }

    public List<Map<String, Object>> listMedia(boolean activeOnly) {
        String sql = activeOnly
                ? "SELECT id, slot_key, label, image_url, link_url, sort_order, active FROM site_media WHERE active = 1 ORDER BY sort_order, slot_key"
                : "SELECT id, slot_key, label, image_url, link_url, sort_order, active FROM site_media ORDER BY sort_order, slot_key";
        return jdbc.query(sql, (rs, i) -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", rs.getString("id"));
            row.put("slotKey", rs.getString("slot_key"));
            row.put("label", rs.getString("label"));
            row.put("imageUrl", rs.getString("image_url"));
            row.put("linkUrl", rs.getString("link_url"));
            row.put("sortOrder", rs.getInt("sort_order"));
            row.put("active", rs.getInt("active") == 1);
            return row;
        });
    }

    public Map<String, String> saveSettings(Map<String, String> incoming) {
        Instant now = Instant.now();
        if (incoming != null) {
            incoming.forEach((key, value) -> {
                if (key == null || key.isBlank()) {
                    return;
                }
                Integer count = jdbc.queryForObject(
                        "SELECT COUNT(*) FROM site_settings WHERE setting_key = ?", Integer.class, key.trim());
                if (count != null && count > 0) {
                    jdbc.update("UPDATE site_settings SET setting_value = ?, updated_at = ? WHERE setting_key = ?",
                            value, Timestamp.from(now), key.trim());
                } else {
                    jdbc.update("INSERT INTO site_settings(setting_key, setting_value, updated_at) VALUES (?,?,?)",
                            key.trim(), value, Timestamp.from(now));
                }
            });
        }
        return settingsMap();
    }

    public Map<String, Object> savePage(String pageKey, Map<String, Object> body) {
        if (pageKey == null || pageKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "page key required");
        }
        Instant now = Instant.now();
        String title = str(body, "title");
        String subtitle = str(body, "subtitle");
        String bodyHtml = str(body, "bodyHtml");
        String hero = str(body, "heroImageUrl");
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM site_pages WHERE page_key = ?", Integer.class, pageKey);
        if (count != null && count > 0) {
            jdbc.update("""
                    UPDATE site_pages SET title = ?, subtitle = ?, body_html = ?, hero_image_url = ?, updated_at = ?
                    WHERE page_key = ?
                    """, title, subtitle, bodyHtml, hero, Timestamp.from(now), pageKey);
        } else {
            jdbc.update("""
                    INSERT INTO site_pages(page_key, title, subtitle, body_html, hero_image_url, meta_json, updated_at)
                    VALUES (?,?,?,?,?,NULL,?)
                    """, pageKey, title, subtitle, bodyHtml, hero, Timestamp.from(now));
        }
        return pagesMap().get(pageKey);
    }

    public Map<String, Object> saveMedia(Map<String, Object> body) {
        Instant now = Instant.now();
        String id = str(body, "id");
        String slot = str(body, "slotKey");
        if (slot == null || slot.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "slotKey required");
        }
        String label = str(body, "label");
        String imageUrl = str(body, "imageUrl");
        String linkUrl = str(body, "linkUrl");
        int sort = body.get("sortOrder") instanceof Number n ? n.intValue() : 0;
        boolean active = !(body.get("active") instanceof Boolean b) || b;
        if (imageUrl == null || imageUrl.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "imageUrl required");
        }
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
            jdbc.update("""
                    INSERT INTO site_media(id, slot_key, label, image_url, link_url, sort_order, active, updated_at)
                    VALUES (?,?,?,?,?,?,?,?)
                    ON DUPLICATE KEY UPDATE label = VALUES(label), image_url = VALUES(image_url),
                      link_url = VALUES(link_url), sort_order = VALUES(sort_order), active = VALUES(active),
                      updated_at = VALUES(updated_at)
                    """,
                    id, slot, label, imageUrl, linkUrl, sort, active ? 1 : 0, Timestamp.from(now));
        } else {
            jdbc.update("""
                    UPDATE site_media SET slot_key = ?, label = ?, image_url = ?, link_url = ?,
                      sort_order = ?, active = ?, updated_at = ? WHERE id = ?
                    """,
                    slot, label, imageUrl, linkUrl, sort, active ? 1 : 0, Timestamp.from(now), id);
        }
        return listMedia(false).stream()
                .filter(m -> slot.equals(m.get("slotKey")))
                .findFirst()
                .orElseThrow();
    }

    private static String str(Map<String, Object> body, String key) {
        if (body == null || body.get(key) == null) {
            return null;
        }
        String value = String.valueOf(body.get(key)).trim();
        return value.isBlank() ? null : value;
    }
}
