package com.radiostreaming.api.saas.repository;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.radiostreaming.api.saas.model.SaasPlanDocument;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class SaasPlanJdbcRepository implements SaasPlanRepository {

    private static final String COLUMNS = """
            id, code, name, description, price_cents, price_currency, credits_included,
            credit_cost_ocr, credit_cost_ai_image, credit_cost_sikh_history, credit_cost_gurbani_ai,
            daily_limit_sikh_history, daily_limit_gurbani_ai,
            features_json, active, sort_order, created_at, updated_at
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final RowMapper<SaasPlanDocument> rowMapper;

    public SaasPlanJdbcRepository(
            @Qualifier("saasJdbcTemplate") JdbcTemplate jdbc,
            ObjectMapper objectMapper) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.rowMapper = (rs, rowNum) -> {
            SaasPlanDocument p = new SaasPlanDocument();
            p.setId(rs.getString("id"));
            p.setCode(rs.getString("code"));
            p.setName(rs.getString("name"));
            p.setDescription(rs.getString("description"));
            p.setPriceCents(rs.getInt("price_cents"));
            String currency = rs.getString("price_currency");
            p.setPriceCurrency(currency == null || currency.isBlank() ? "INR" : currency);
            p.setCreditsIncluded(rs.getLong("credits_included"));
            p.setCreditCostOcr(rs.getInt("credit_cost_ocr"));
            p.setCreditCostAiImage(rs.getInt("credit_cost_ai_image"));
            p.setCreditCostSikhHistory(rs.getInt("credit_cost_sikh_history"));
            p.setCreditCostGurbaniAi(rs.getInt("credit_cost_gurbani_ai"));
            p.setDailyLimitSikhHistory(rs.getInt("daily_limit_sikh_history"));
            p.setDailyLimitGurbaniAi(rs.getInt("daily_limit_gurbani_ai"));
            String featuresJson = rs.getString("features_json");
            p.setFeatures(new ArrayList<>(SaasJdbcSupport.readStringList(objectMapper, featuresJson)));
            p.setActive(SaasJdbcSupport.toBoolean(rs.getInt("active")));
            p.setSortOrder(rs.getInt("sort_order"));
            p.setCreatedAt(SaasJdbcSupport.toInstant(rs.getTimestamp("created_at")));
            p.setUpdatedAt(SaasJdbcSupport.toInstant(rs.getTimestamp("updated_at")));
            return p;
        };
    }

    @Override
    public Optional<SaasPlanDocument> findById(String id) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT " + COLUMNS + " FROM saas_plans WHERE id = ?",
                    rowMapper,
                    id));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }

    @Override
    public SaasPlanDocument save(SaasPlanDocument plan) {
        plan.setId(SaasJdbcSupport.newIdIfBlank(plan.getId()));
        if (plan.getPriceCurrency() == null || plan.getPriceCurrency().isBlank()) {
            plan.setPriceCurrency("INR");
        }
        String featuresJson = SaasJdbcSupport.writeJson(objectMapper, plan.getFeatures());
        boolean exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM saas_plans WHERE id = ?",
                Integer.class,
                plan.getId()) > 0;
        if (exists) {
            jdbc.update("""
                    UPDATE saas_plans SET
                      code = ?, name = ?, description = ?, price_cents = ?, price_currency = ?,
                      credits_included = ?,
                      credit_cost_ocr = ?, credit_cost_ai_image = ?, credit_cost_sikh_history = ?,
                      credit_cost_gurbani_ai = ?, daily_limit_sikh_history = ?, daily_limit_gurbani_ai = ?,
                      features_json = ?, active = ?, sort_order = ?,
                      created_at = ?, updated_at = ?
                    WHERE id = ?
                    """,
                    plan.getCode(),
                    plan.getName(),
                    plan.getDescription(),
                    plan.getPriceCents(),
                    plan.getPriceCurrency(),
                    plan.getCreditsIncluded(),
                    plan.getCreditCostOcr(),
                    plan.getCreditCostAiImage(),
                    plan.getCreditCostSikhHistory(),
                    plan.getCreditCostGurbaniAi(),
                    plan.getDailyLimitSikhHistory(),
                    plan.getDailyLimitGurbaniAi(),
                    featuresJson,
                    SaasJdbcSupport.toTinyInt(plan.isActive()),
                    plan.getSortOrder(),
                    SaasJdbcSupport.toTimestamp(plan.getCreatedAt()),
                    SaasJdbcSupport.toTimestamp(plan.getUpdatedAt()),
                    plan.getId());
        } else {
            jdbc.update("""
                    INSERT INTO saas_plans (
                      id, code, name, description, price_cents, price_currency, credits_included,
                      credit_cost_ocr, credit_cost_ai_image, credit_cost_sikh_history, credit_cost_gurbani_ai,
                      daily_limit_sikh_history, daily_limit_gurbani_ai,
                      features_json, active, sort_order, created_at, updated_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    plan.getId(),
                    plan.getCode(),
                    plan.getName(),
                    plan.getDescription(),
                    plan.getPriceCents(),
                    plan.getPriceCurrency(),
                    plan.getCreditsIncluded(),
                    plan.getCreditCostOcr(),
                    plan.getCreditCostAiImage(),
                    plan.getCreditCostSikhHistory(),
                    plan.getCreditCostGurbaniAi(),
                    plan.getDailyLimitSikhHistory(),
                    plan.getDailyLimitGurbaniAi(),
                    featuresJson,
                    SaasJdbcSupport.toTinyInt(plan.isActive()),
                    plan.getSortOrder(),
                    SaasJdbcSupport.toTimestamp(plan.getCreatedAt()),
                    SaasJdbcSupport.toTimestamp(plan.getUpdatedAt()));
        }
        return plan;
    }

    @Override
    public List<SaasPlanDocument> findAll() {
        return jdbc.query("SELECT " + COLUMNS + " FROM saas_plans ORDER BY sort_order ASC", rowMapper);
    }

    @Override
    public long count() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM saas_plans", Long.class);
        return n == null ? 0L : n;
    }

    @Override
    public void delete(SaasPlanDocument plan) {
        if (plan != null && plan.getId() != null) {
            jdbc.update("DELETE FROM saas_plans WHERE id = ?", plan.getId());
        }
    }

    @Override
    public List<SaasPlanDocument> findByActiveTrueOrderBySortOrderAsc() {
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM saas_plans WHERE active = 1 ORDER BY sort_order ASC",
                rowMapper);
    }

    @Override
    public Optional<SaasPlanDocument> findByCode(String code) {
        try {
            return Optional.ofNullable(jdbc.queryForObject(
                    "SELECT " + COLUMNS + " FROM saas_plans WHERE code = ?",
                    rowMapper,
                    code));
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
    }
}

