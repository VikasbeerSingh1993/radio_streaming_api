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
            planRepository.save(plan("starter", "Starter", "Try Punjabi OCR & AI images", 900, 200, 5, 10, 2,
                    defaultFeatures(200), 1, now));
            planRepository.save(plan("growth", "Growth", "For small teams and apps", 2900, 1000, 4, 8, 2,
                    defaultFeatures(1000), 2, now));
            planRepository.save(plan("scale", "Scale", "High volume SaaS usage", 9900, 5000, 3, 6, 1,
                    defaultFeatures(5000), 3, now));
            log.info("Seeded default SaaS plans in Mongo collection saas_plans");
        } else {
            // Keep existing prices/credits; ensure Sikh History is listed and costed.
            for (SaasPlanDocument existing : planRepository.findAll()) {
                boolean changed = false;
                if (existing.getCreditCostSikhHistory() <= 0) {
                    int cost = "scale".equalsIgnoreCase(existing.getCode()) ? 1 : 2;
                    existing.setCreditCostSikhHistory(cost);
                    changed = true;
                }
                List<String> features = existing.getFeatures() == null
                        ? new ArrayList<>()
                        : new ArrayList<>(existing.getFeatures());
                if (features.stream().noneMatch(f -> f.toLowerCase().contains("sikh history"))) {
                    features.add("AI Sikh History search");
                    existing.setFeatures(features);
                    changed = true;
                }
                if (changed) {
                    existing.setUpdatedAt(now);
                    planRepository.save(existing);
                }
            }
            log.info("Ensured SaaS plans include AI Sikh History search");
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
            log.info("Seeded SaaS admin saas-admin@divinebliss.app (change password after first login)");
        });
    }

    private static List<String> defaultFeatures(long credits) {
        return List.of(
                credits + " credits",
                "Punjabi OCR",
                "AI image generation",
                "AI Sikh History search",
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
        p.setFeatures(features);
        p.setActive(true);
        p.setSortOrder(sort);
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        return p;
    }
}
