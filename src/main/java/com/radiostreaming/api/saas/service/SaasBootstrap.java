package com.radiostreaming.api.saas.service;

import com.radiostreaming.api.saas.model.SaasPlanDocument;
import com.radiostreaming.api.saas.model.SaasUserDocument;
import com.radiostreaming.api.saas.repository.SaasPlanRepository;
import com.radiostreaming.api.saas.repository.SaasUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Seeds / refreshes INR AI subscription plans in MySQL {@code divine_bliss_web.saas_plans}.
 * Pricing targets Azure GPT-4o history cost (~₹40 for 5 queries/day) with healthy margin.
 */
@Component
public class SaasBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SaasBootstrap.class);
    private static final int HARD_MAX_DAILY = 100;
    private static final Set<String> LEGACY_CODES = Set.of("starter", "growth", "scale");

    private final SaasPlanRepository planRepository;
    private final SaasUserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public SaasBootstrap(
            SaasPlanRepository planRepository,
            SaasUserRepository userRepository,
            PasswordEncoder passwordEncoder) {
        this.planRepository = planRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(20)
    public void seedPlansAndAdmin() {
        Instant now = Instant.now();
        upsertAiPlans(now);
        deactivateLegacyPlans(now);

        userRepository.findByEmailIgnoreCase("saas-admin@divinebliss.app").ifPresentOrElse(existing -> {
            if (!"SAAS_ADMIN".equals(existing.getRole())) {
                existing.setRole("SAAS_ADMIN");
                userRepository.save(existing);
            }
        }, () -> {
            SaasUserDocument admin = new SaasUserDocument();
            admin.setEmail("saas-admin@divinebliss.app");
            admin.setDisplayName("SaaS Admin");
            admin.setPasswordHash(passwordEncoder.encode("ChangeMeSaaS!23"));
            admin.setRole("SAAS_ADMIN");
            admin.setEnabled(true);
            admin.setCreditsRemaining(10_000);
            admin.setPlanName("Internal");
            admin.setCreatedAt(now);
            admin.setUpdatedAt(now);
            userRepository.save(admin);
            log.info("Seeded SaaS admin saas-admin@divinebliss.app");
        });
    }

    private void upsertAiPlans(Instant now) {
        // Azure ~₹0.45/History query; Free ₹100 covers ~150/mo (~₹40 Azure) with ~₹60 margin.
        // Credits sized so History cost 2 ≈ monthly History quota + OCR/image headroom.
        upsert(plan("ai-free", "AI Free Tier",
                "Required to use AI History, OCR, AI Images, and Gurbani AI Search (voice). 5 Sikh History questions per day.",
                10_000, 300, 5, 10, 2, 3, 5, 5,
                List.of(
                        "₹100 / month",
                        "5 Sikh History questions / day",
                        "300 credits (History, OCR, AI images, voice Gurbani search)",
                        "Upgrade anytime to Basic, Pro, or Max"),
                1, now));

        upsert(plan("ai-basic", "AI Basic",
                "More daily History capacity for regular use. Azure cost covered with margin.",
                39_900, 900, 4, 8, 2, 3, 20, 20,
                List.of(
                        "₹399 / month",
                        "20 Sikh History questions / day",
                        "900 credits",
                        "OCR, AI images, and Gurbani AI Search included"),
                2, now));

        upsert(plan("ai-pro", "AI Pro",
                "Higher daily History limit for power users and seva teams.",
                99_900, 2_000, 3, 6, 2, 2, 50, 50,
                List.of(
                        "₹999 / month",
                        "50 Sikh History questions / day",
                        "2,000 credits",
                        "Lower per-call credit costs"),
                3, now));

        upsert(plan("ai-max", "AI Max",
                "Maximum daily History capacity (100/day hard cap).",
                189_900, 4_000, 3, 5, 1, 2, HARD_MAX_DAILY, HARD_MAX_DAILY,
                List.of(
                        "₹1,899 / month",
                        "100 Sikh History questions / day (maximum)",
                        "4,000 credits",
                        "Best rates for high daily usage"),
                4, now));

        log.info("Ensured AI subscription plans (INR) in MySQL divine_bliss_web.saas_plans");
    }

    private void deactivateLegacyPlans(Instant now) {
        for (SaasPlanDocument existing : planRepository.findAll()) {
            String code = existing.getCode() == null ? "" : existing.getCode().toLowerCase(Locale.ROOT);
            if (LEGACY_CODES.contains(code) && existing.isActive()) {
                existing.setActive(false);
                existing.setUpdatedAt(now);
                planRepository.save(existing);
                log.info("Deactivated legacy plan code={}", code);
            }
        }
    }

    private void upsert(SaasPlanDocument desired) {
        planRepository.findByCode(desired.getCode()).ifPresentOrElse(existing -> {
            existing.setName(desired.getName());
            existing.setDescription(desired.getDescription());
            existing.setPriceCents(desired.getPriceCents());
            existing.setPriceCurrency(desired.getPriceCurrency());
            existing.setCreditsIncluded(desired.getCreditsIncluded());
            existing.setCreditCostOcr(desired.getCreditCostOcr());
            existing.setCreditCostAiImage(desired.getCreditCostAiImage());
            existing.setCreditCostSikhHistory(desired.getCreditCostSikhHistory());
            existing.setCreditCostGurbaniAi(desired.getCreditCostGurbaniAi());
            existing.setDailyLimitSikhHistory(desired.getDailyLimitSikhHistory());
            existing.setDailyLimitGurbaniAi(desired.getDailyLimitGurbaniAi());
            existing.setFeatures(desired.getFeatures());
            existing.setActive(true);
            existing.setSortOrder(desired.getSortOrder());
            existing.setUpdatedAt(desired.getUpdatedAt());
            planRepository.save(existing);
        }, () -> planRepository.save(desired));
    }

    private static SaasPlanDocument plan(
            String code,
            String name,
            String description,
            int pricePaise,
            long credits,
            int ocrCost,
            int imageCost,
            int sikhHistoryCost,
            int gurbaniAiCost,
            int dailyHistory,
            int dailyGurbaniAi,
            List<String> features,
            int sort,
            Instant now) {
        SaasPlanDocument p = new SaasPlanDocument();
        p.setCode(code);
        p.setName(name);
        p.setDescription(description);
        p.setPriceCents(pricePaise);
        p.setPriceCurrency("INR");
        p.setCreditsIncluded(credits);
        p.setCreditCostOcr(ocrCost);
        p.setCreditCostAiImage(imageCost);
        p.setCreditCostSikhHistory(sikhHistoryCost);
        p.setCreditCostGurbaniAi(gurbaniAiCost);
        p.setDailyLimitSikhHistory(Math.min(HARD_MAX_DAILY, Math.max(1, dailyHistory)));
        p.setDailyLimitGurbaniAi(Math.min(HARD_MAX_DAILY, Math.max(1, dailyGurbaniAi)));
        p.setFeatures(features);
        p.setActive(true);
        p.setSortOrder(sort);
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        return p;
    }
}

