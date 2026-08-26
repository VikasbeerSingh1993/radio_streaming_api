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
        if (planRepository.count() == 0) {
            Instant now = Instant.now();
            planRepository.save(plan("starter", "Starter", "Try Punjabi OCR & AI images", 900, 200, 5, 10,
                    List.of("200 credits", "Punjabi OCR", "AI image generation", "API key access"), 1, now));
            planRepository.save(plan("growth", "Growth", "For small teams and apps", 2900, 1000, 4, 8,
                    List.of("1,000 credits", "Lower per-call cost", "Overage option", "Email support"), 2, now));
            planRepository.save(plan("scale", "Scale", "High volume SaaS usage", 9900, 5000, 3, 6,
                    List.of("5,000 credits", "Best unit rates", "Priority support", "Usage analytics"), 3, now));
            log.info("Seeded default SaaS plans in Mongo collection saas_plans");
        }

        userRepository.findByEmailIgnoreCase("saas-admin@divinebliss.app").ifPresentOrElse(existing -> {
            if (!"SAAS_ADMIN".equals(existing.getRole())) {
                existing.setRole("SAAS_ADMIN");
                userRepository.save(existing);
            }
        }, () -> {
            Instant now = Instant.now();
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

    private static SaasPlanDocument plan(
            String code,
            String name,
            String description,
            int priceCents,
            long credits,
            int ocrCost,
            int imageCost,
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
        p.setFeatures(features);
        p.setActive(true);
        p.setSortOrder(sort);
        p.setCreatedAt(now);
        p.setUpdatedAt(now);
        return p;
    }
}
