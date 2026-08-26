package com.radiostreaming.api.saas.repository;

import com.radiostreaming.api.saas.model.SaasUserDocument;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class SaasUserJdbcRepository implements SaasUserRepository {

    private static final String COLUMNS = """
            id, email, display_name, password_hash, role, enabled, plan_id, plan_name,
            credits_remaining, credits_used, credits_pending, allow_ocr_overage, allow_ai_image_overage,
            created_at, updated_at
            """;

    private static final RowMapper<SaasUserDocument> ROW_MAPPER = (rs, rowNum) -> {
        SaasUserDocument u = new SaasUserDocument();
        u.setId(rs.getString("id"));
        u.setEmail(rs.getString("email"));
        u.setDisplayName(rs.getString("display_name"));
        u.setPasswordHash(rs.getString("password_hash"));
        u.setRole(rs.getString("role"));
        u.setEnabled(SaasJdbcSupport.toBoolean(rs.getInt("enabled")));
        u.setPlanId(rs.getString("plan_id"));
        u.setPlanName(rs.getString("plan_name"));
        u.setCreditsRemaining(rs.getLong("credits_remaining"));
        u.setCreditsUsed(rs.getLong("credits_used"));
        u.setCreditsPending(rs.getLong("credits_pending"));
        u.setAllowOcrOverage(SaasJdbcSupport.toBoolean(rs.getInt("allow_ocr_overage")));
        u.setAllowAiImageOverage(SaasJdbcSupport.toBoolean(rs.getInt("allow_ai_image_overage")));
        u.setCreatedAt(SaasJdbcSupport.toInstant(rs.getTimestamp("created_at")));
        u.setUpdatedAt(SaasJdbcSupport.toInstant(rs.getTimestamp("updated_at")));
        return u;
    };

    private final JdbcTemplate jdbc;

    public SaasUserJdbcRepository(@Qualifier("saasJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SaasUserDocument> findById(String id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT " + COLUMNS + " FROM saas_users WHERE id = ?",
                    ROW_MAPPER,
                    id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public SaasUserDocument save(SaasUserDocument user) {
        user.setId(SaasJdbcSupport.newIdIfBlank(user.getId()));
        boolean exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM saas_users WHERE id = ?",
                Integer.class,
                user.getId()) > 0;
        if (exists) {
            jdbc.update("""
                    UPDATE saas_users SET
                      email = ?, display_name = ?, password_hash = ?, role = ?, enabled = ?,
                      plan_id = ?, plan_name = ?, credits_remaining = ?, credits_used = ?, credits_pending = ?,
                      allow_ocr_overage = ?, allow_ai_image_overage = ?, created_at = ?, updated_at = ?
                    WHERE id = ?
                    """,
                    user.getEmail(),
                    user.getDisplayName(),
                    user.getPasswordHash(),
                    user.getRole(),
                    SaasJdbcSupport.toTinyInt(user.isEnabled()),
                    user.getPlanId(),
                    user.getPlanName(),
                    user.getCreditsRemaining(),
                    user.getCreditsUsed(),
                    user.getCreditsPending(),
                    SaasJdbcSupport.toTinyInt(user.isAllowOcrOverage()),
                    SaasJdbcSupport.toTinyInt(user.isAllowAiImageOverage()),
                    SaasJdbcSupport.toTimestamp(user.getCreatedAt()),
                    SaasJdbcSupport.toTimestamp(user.getUpdatedAt()),
                    user.getId());
        } else {
            jdbc.update("""
                    INSERT INTO saas_users (
                      id, email, display_name, password_hash, role, enabled, plan_id, plan_name,
                      credits_remaining, credits_used, credits_pending, allow_ocr_overage, allow_ai_image_overage,
                      created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    user.getId(),
                    user.getEmail(),
                    user.getDisplayName(),
                    user.getPasswordHash(),
                    user.getRole(),
                    SaasJdbcSupport.toTinyInt(user.isEnabled()),
                    user.getPlanId(),
                    user.getPlanName(),
                    user.getCreditsRemaining(),
                    user.getCreditsUsed(),
                    user.getCreditsPending(),
                    SaasJdbcSupport.toTinyInt(user.isAllowOcrOverage()),
                    SaasJdbcSupport.toTinyInt(user.isAllowAiImageOverage()),
                    SaasJdbcSupport.toTimestamp(user.getCreatedAt()),
                    SaasJdbcSupport.toTimestamp(user.getUpdatedAt()));
        }
        return user;
    }

    @Override
    public List<SaasUserDocument> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM saas_users ORDER BY created_at DESC", ROW_MAPPER);
    }

    @Override
    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM saas_users", Long.class);
        return n == null ? 0L : n;
    }

    @Override
    public void delete(SaasUserDocument user) {
        if (user != null && user.getId() != null) {
            jdbc.update("DELETE FROM saas_users WHERE id = ?", user.getId());
        }
    }

    @Override
    public Optional<SaasUserDocument> findByEmailIgnoreCase(String email) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT " + COLUMNS + " FROM saas_users WHERE LOWER(email) = LOWER(?)",
                    ROW_MAPPER,
                    email));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public boolean existsByEmailIgnoreCase(String email) {
        Integer n = jdbc.queryForObject(
                "SELECT COUNT(*) FROM saas_users WHERE LOWER(email) = LOWER(?)",
                Integer.class,
                email);
        return n != null && n > 0;
    }
}
