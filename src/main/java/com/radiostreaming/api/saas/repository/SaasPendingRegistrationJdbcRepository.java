package com.radiostreaming.api.saas.repository;

import com.radiostreaming.api.saas.model.SaasPendingRegistrationDocument;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class SaasPendingRegistrationJdbcRepository implements SaasPendingRegistrationRepository {

    private static final String COLUMNS = """
            id, email, first_name, last_name, password_hash, otp_hash, otp_expires_at,
            send_count, verify_attempts, created_at, updated_at
            """;

    private static final RowMapper<SaasPendingRegistrationDocument> ROW_MAPPER = (rs, rowNum) -> {
        SaasPendingRegistrationDocument p = new SaasPendingRegistrationDocument();
        p.setId(rs.getString("id"));
        p.setEmail(rs.getString("email"));
        p.setFirstName(rs.getString("first_name"));
        p.setLastName(rs.getString("last_name"));
        p.setPasswordHash(rs.getString("password_hash"));
        p.setOtpHash(rs.getString("otp_hash"));
        p.setOtpExpiresAt(SaasJdbcSupport.toInstant(rs.getTimestamp("otp_expires_at")));
        p.setSendCount(rs.getInt("send_count"));
        p.setVerifyAttempts(rs.getInt("verify_attempts"));
        p.setCreatedAt(SaasJdbcSupport.toInstant(rs.getTimestamp("created_at")));
        p.setUpdatedAt(SaasJdbcSupport.toInstant(rs.getTimestamp("updated_at")));
        return p;
    };

    private final JdbcTemplate jdbc;

    public SaasPendingRegistrationJdbcRepository(@Qualifier("saasJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SaasPendingRegistrationDocument> findById(String id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT " + COLUMNS + " FROM saas_pending_registrations WHERE id = ?",
                    ROW_MAPPER,
                    id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public SaasPendingRegistrationDocument save(SaasPendingRegistrationDocument pending) {
        pending.setId(SaasJdbcSupport.newIdIfBlank(pending.getId()));
        boolean exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM saas_pending_registrations WHERE id = ?",
                Integer.class,
                pending.getId()) > 0;
        if (exists) {
            jdbc.update("""
                    UPDATE saas_pending_registrations SET
                      email = ?, first_name = ?, last_name = ?, password_hash = ?, otp_hash = ?,
                      otp_expires_at = ?, send_count = ?, verify_attempts = ?, created_at = ?, updated_at = ?
                    WHERE id = ?
                    """,
                    pending.getEmail(),
                    pending.getFirstName(),
                    pending.getLastName(),
                    pending.getPasswordHash(),
                    pending.getOtpHash(),
                    SaasJdbcSupport.toTimestamp(pending.getOtpExpiresAt()),
                    pending.getSendCount(),
                    pending.getVerifyAttempts(),
                    SaasJdbcSupport.toTimestamp(pending.getCreatedAt()),
                    SaasJdbcSupport.toTimestamp(pending.getUpdatedAt()),
                    pending.getId());
        } else {
            jdbc.update("""
                    INSERT INTO saas_pending_registrations (
                      id, email, first_name, last_name, password_hash, otp_hash, otp_expires_at,
                      send_count, verify_attempts, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    pending.getId(),
                    pending.getEmail(),
                    pending.getFirstName(),
                    pending.getLastName(),
                    pending.getPasswordHash(),
                    pending.getOtpHash(),
                    SaasJdbcSupport.toTimestamp(pending.getOtpExpiresAt()),
                    pending.getSendCount(),
                    pending.getVerifyAttempts(),
                    SaasJdbcSupport.toTimestamp(pending.getCreatedAt()),
                    SaasJdbcSupport.toTimestamp(pending.getUpdatedAt()));
        }
        return pending;
    }

    @Override
    public void delete(SaasPendingRegistrationDocument pending) {
        if (pending != null && pending.getId() != null) {
            jdbc.update("DELETE FROM saas_pending_registrations WHERE id = ?", pending.getId());
        }
    }

    @Override
    public Optional<SaasPendingRegistrationDocument> findByEmailIgnoreCase(String email) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT " + COLUMNS + " FROM saas_pending_registrations WHERE LOWER(email) = LOWER(?)",
                    ROW_MAPPER,
                    email));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public void deleteByEmailIgnoreCase(String email) {
        jdbc.update("DELETE FROM saas_pending_registrations WHERE LOWER(email) = LOWER(?)", email);
    }
}
