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
import java.util.ArrayList;
import java.util.List;

@Component
public class SaasBootstrap {

    private static final Logger log = LoggerFactory.getLogger(SaasBootstrap.class);

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
        if (planRepository.count() == 0) {
            planRepository.save(plan("starter", "Starter", "Credits for OCR, AI images, and AI search tools",
                    900, 200, 5, 10, 2, 3, defaultFeatures(200), 1, now));
            planRepository.save(plan("growth", "Growth", "More credits for growing teams and apps",
                    2900, 1000, 4, 8, 2, 3, defaultFeatures(1000), 2, now));
            planRepository.save(plan("scale", "Scale", "Best rates for high daily usage",
                    9900, 5000, 3, 6, 1, 2, defaultFeatures(5000), 3, now));
            log.info("Seeded SaaS plans in MySQL divine_bliss_web.saas_plans");
        } else {
            for (SaasPlanDocument existing : planRepository.findAll()) {
                boolean changed = false;
                if (existing.getCreditCostSikhHistory() <= 0) {
                    existing.setCreditCostSikhHistory("scale".equalsIgnoreCase(existing.getCode()) ? 1 : 2);
                    changed = true;
                }
                if (existing.getCreditCostGurbaniAi() <= 0) {
                    existing.setCreditCostGurbaniAi("scale".equalsIgnoreCase(existing.getCode()) ? 2 : 3);
                    changed = true;
                }
                List<String> features = existing.getFeatures() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(existing.getFeatures());
                if (features.stream().noneMatch(f -> f.toLowerCase().contains("sikh history"))) {
                    features.add("AI Sikh History search");
                    changed = true;
                }
                if (features.stream().noneMatch(f -> f.toLowerCase().contains("gurbani ai"))) {
                    features.add("Gurbani AI search");
                    changed = true;
                }
                // Drop demo/jargon labels from description if present
                if (existing.getDescription() != null && existing.getDescription().toLowerCase().contains("demo")) {
                    existing.setDescription("Credits for OCR, AI images, and AI search tools");
                    changed = true;
                }
                existing.setFeatures(features);
                if (changed) {
                    existing.setUpdatedAt(now);
                    planRepository.save(existing);
                }
            }
            log.info("Ensured SaaS plans include Sikh History and Gurbani AI search");
        }

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

    private static List<String> defaultFeatures(long credits) {
        return List.of(
                credits + " credits",
                "Punjabi OCR",
                "AI image generation",
                "AI Sikh History search",
                "Gurbani AI search",
                "API key access");
    }

    private static SaasPlanDocument plan(
            String code,
            String name,
            String description,
            int priceCents,
            long credits,
            int ocrCost,
            int imageCost,
            int sikhHistoryCost,
            int gurbaniAiCost,
            List<String> features,
            int sort,
            Instant now) {
        SaasPlanDocument p = new SaasPlanDocument();
        p.setCode(code);
        p.setName(name);
        p.setDescription(description);
        p.setPriceCents(priceCents);
        p.setCreditsIncluded(credits);
        p.setCreditCostOcr(ocrCost);
        p.setCreditCostAiImage(imageCost);
        p.setCreditCostSikhHistory(sikhHistoryCost);
        p.setCreditCostGurbaniAi(gurbaniAiCost);
        p.setFeatures(features);
        p.setActive(true);
        p.setSortOrder(sort);
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        return p;
    }
}
