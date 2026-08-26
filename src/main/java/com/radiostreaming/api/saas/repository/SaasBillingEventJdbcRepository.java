package com.radiostreaming.api.saas.repository;

import com.radiostreaming.api.saas.model.SaasBillingEventDocument;
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
public class SaasBillingEventJdbcRepository implements SaasBillingEventRepository {

    private static final String COLUMNS = """
            id, user_id, plan_id, plan_name, type, amount_cents, credits_added, note, status, created_at
            """;

    private static final RowMapper<SaasBillingEventDocument> ROW_MAPPER = (rs, rowNum) -> {
        SaasBillingEventDocument e = new SaasBillingEventDocument();
        e.setId(rs.getString("id"));
        e.setUserId(rs.getString("user_id"));
        e.setPlanId(rs.getString("plan_id"));
        e.setPlanName(rs.getString("plan_name"));
        e.setType(rs.getString("type"));
        e.setAmountCents(rs.getInt("amount_cents"));
        e.setCreditsAdded(rs.getLong("credits_added"));
        e.setNote(rs.getString("note"));
        e.setStatus(rs.getString("status"));
        e.setCreatedAt(SaasJdbcSupport.toInstant(rs.getTimestamp("created_at")));
        return e;
    };

    private final JdbcTemplate jdbc;

    public SaasBillingEventJdbcRepository(@Qualifier("saasJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<SaasBillingEventDocument> findById(String id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT " + COLUMNS + " FROM saas_billing_events WHERE id = ?",
                    ROW_MAPPER,
                    id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public SaasBillingEventDocument save(SaasBillingEventDocument event) {
        event.setId(SaasJdbcSupport.newIdIfBlank(event.getId()));
        boolean exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM saas_billing_events WHERE id = ?",
                Integer.class,
                event.getId()) > 0;
        if (exists) {
            jdbc.update("""
                    UPDATE saas_billing_events SET
                      user_id = ?, plan_id = ?, plan_name = ?, type = ?, amount_cents = ?,
                      credits_added = ?, note = ?, status = ?, created_at = ?
                    WHERE id = ?
                    """,
                    event.getUserId(),
                    event.getPlanId(),
                    event.getPlanName(),
                    event.getType(),
                    event.getAmountCents(),
                    event.getCreditsAdded(),
                    event.getNote(),
                    event.getStatus(),
                    SaasJdbcSupport.toTimestamp(event.getCreatedAt()),
                    event.getId());
        } else {
            jdbc.update("""
                    INSERT INTO saas_billing_events (
                      id, user_id, plan_id, plan_name, type, amount_cents, credits_added, note, status, created_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    event.getId(),
                    event.getUserId(),
                    event.getPlanId(),
                    event.getPlanName(),
                    event.getType(),
                    event.getAmountCents(),
                    event.getCreditsAdded(),
                    event.getNote(),
                    event.getStatus(),
                    SaasJdbcSupport.toTimestamp(event.getCreatedAt()));
        }
        return event;
    }

    @Override
    public List<SaasBillingEventDocument> findAll() {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM saas_billing_events ORDER BY created_at DESC",
                ROW_MAPPER);
    }

    @Override
    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM saas_billing_events", Long.class);
        return n == null ? 0L : n;
    }

    @Override
    public void delete(SaasBillingEventDocument event) {
        if (event != null && event.getId() != null) {
            jdbc.update("DELETE FROM saas_billing_events WHERE id = ?", event.getId());
        }
    }

    @Override
    public Page<SaasBillingEventDocument> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable) {
        Long totalObj = jdbc.queryForObject(
                "SELECT COUNT(*) FROM saas_billing_events WHERE user_id = ?",
                Long.class,
                userId);
        long total = totalObj == null ? 0L : totalObj;
        List<SaasBillingEventDocument> content = jdbc.query(
                "SELECT " + COLUMNS + " FROM saas_billing_events WHERE user_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?",
                ROW_MAPPER,
                userId,
                pageable.getPageSize(),
                pageable.getOffset());
        return new PageImpl<>(content, pageable, total);
    }
}
