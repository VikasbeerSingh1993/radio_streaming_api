package com.radiostreaming.api.saas.repository;

import com.radiostreaming.api.saas.model.SaasApiKeyDocument;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class SaasApiKeyJdbcRepository implements SaasApiKeyRepository {

    private static final String COLUMNS = """
            id, user_id, name, key_prefix, key_hash, revoked, last_used_at, created_at, hit_count
            """;

    private static final RowMapper<SaasApiKeyDocument> ROW_MAPPER = (rs, rowNum) -> {
        SaasApiKeyDocument k = new SaasApiKeyDocument();
        k.setId(rs.getString("id"));
        k.setUserId(rs.getString("user_id"));
        k.setName(rs.getString("name"));
        k.setKeyPrefix(rs.getString("key_prefix"));
        k.setKeyHash(rs.getString("key_hash"));
        k.setRevoked(SaasJdbcSupport.toBoolean(rs.getInt("revoked")));
        k.setLastUsedAt(SaasJdbcSupport.toInstant(rs.getTimestamp("last_used_at")));
        k.setCreatedAt(SaasJdbcSupport.toInstant(rs.getTimestamp("created_at")));
        k.setHitCount(rs.getLong("hit_count"));
        return k;
    };

    private final JdbcTemplate jdbc;

    public SaasApiKeyJdbcRepository(@Qualifier("saasJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SaasApiKeyDocument> findById(String id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT " + COLUMNS + " FROM saas_api_keys WHERE id = ?",
                    ROW_MAPPER,
                    id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public SaasApiKeyDocument save(SaasApiKeyDocument key) {
        key.setId(SaasJdbcSupport.newIdIfBlank(key.getId()));
        boolean exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM saas_api_keys WHERE id = ?",
                Integer.class,
                key.getId()) > 0;
        if (exists) {
            jdbc.update("""
                    UPDATE saas_api_keys SET
                      user_id = ?, name = ?, key_prefix = ?, key_hash = ?, revoked = ?,
                      last_used_at = ?, created_at = ?, hit_count = ?
                    WHERE id = ?
                    """,
                    key.getUserId(),
                    key.getName(),
                    key.getKeyPrefix(),
                    key.getKeyHash(),
                    SaasJdbcSupport.toTinyInt(key.isRevoked()),
                    SaasJdbcSupport.toTimestamp(key.getLastUsedAt()),
                    SaasJdbcSupport.toTimestamp(key.getCreatedAt()),
                    key.getHitCount(),
                    key.getId());
        } else {
            jdbc.update("""
                    INSERT INTO saas_api_keys (
                      id, user_id, name, key_prefix, key_hash, revoked, last_used_at, created_at, hit_count
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    key.getId(),
                    key.getUserId(),
                    key.getName(),
                    key.getKeyPrefix(),
                    key.getKeyHash(),
                    SaasJdbcSupport.toTinyInt(key.isRevoked()),
                    SaasJdbcSupport.toTimestamp(key.getLastUsedAt()),
                    SaasJdbcSupport.toTimestamp(key.getCreatedAt()),
                    key.getHitCount());
        }
        return key;
    }

    @Override
    public List<SaasApiKeyDocument> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM saas_api_keys ORDER BY created_at DESC", ROW_MAPPER);
    }

    @Override
    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM saas_api_keys", Long.class);
        return n == null ? 0L : n;
    }

    @Override
    public void delete(SaasApiKeyDocument key) {
        if (key != null && key.getId() != null) {
            jdbc.update("DELETE FROM saas_api_keys WHERE id = ?", key.getId());
        }
    }

    @Override
    public List<SaasApiKeyDocument> findByUserIdOrderByCreatedAtDesc(String userId) {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM saas_api_keys WHERE user_id = ? ORDER BY created_at DESC",
                ROW_MAPPER,
                userId);
    }

    @Override
    public Optional<SaasApiKeyDocument> findByKeyHash(String keyHash) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT " + COLUMNS + " FROM saas_api_keys WHERE key_hash = ?",
                    ROW_MAPPER,
                    keyHash));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<SaasApiKeyDocument> findByIdAndUserId(String id, String userId) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT " + COLUMNS + " FROM saas_api_keys WHERE id = ? AND user_id = ?",
                    ROW_MAPPER,
                    id,
                    userId));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}
