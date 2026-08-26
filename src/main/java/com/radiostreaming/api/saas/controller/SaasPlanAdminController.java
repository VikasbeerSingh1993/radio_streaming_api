package com.radiostreaming.api.saas.controller;

import com.radiostreaming.api.saas.model.SaasPlanDocument;
import com.radiostreaming.api.saas.model.SaasUserDocument;
import com.radiostreaming.api.saas.repository.SaasPlanRepository;
import com.radiostreaming.api.saas.repository.SaasUserRepository;
import com.radiostreaming.api.saas.repository.SaasUsageEventRepository;
import com.radiostreaming.api.saas.security.CurrentSaasUser;
import com.radiostreaming.api.billing.BillingService;
import com.radiostreaming.api.saas.service.SaasAuthService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/saas")
public class SaasPlanAdminController {

    private final SaasPlanRepository planRepository;
    private final SaasUserRepository userRepository;
    private final SaasUsageEventRepository usageEventRepository;
    private final BillingService billingService;
    private final CurrentSaasUser currentSaasUser;

    public SaasPlanAdminController(
            SaasPlanRepository planRepository,
            SaasUserRepository userRepository,
            SaasUsageEventRepository usageEventRepository,
            BillingService billingService,
            CurrentSaasUser currentSaasUser) {
        this.planRepository = planRepository;
        this.userRepository = userRepository;
        this.usageEventRepository = usageEventRepository;
        this.billingService = billingService;
        this.currentSaasUser = currentSaasUser;
    }

    @GetMapping("/plans")
    public List<SaasPlanDocument> plans() {
        return planRepository.findByActiveTrueOrderBySortOrderAsc();
    }

    @GetMapping("/admin/plans")
    public List<SaasPlanDocument> adminPlans() {
        requireAdmin();
        return planRepository.findAll();
    }

    @PutMapping("/admin/plans/{id}")
    public SaasPlanDocument updatePlan(@PathVariable String id, @RequestBody SaasPlanDocument incoming) {
        requireAdmin();
        SaasPlanDocument plan = planRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found"));
        if (incoming.getName() != null) {
            plan.setName(incoming.getName());
        }
        if (incoming.getDescription() != null) {
            plan.setDescription(incoming.getDescription());
        }
        if (incoming.getPriceCents() > 0) {
            plan.setPriceCents(incoming.getPriceCents());
        }
        if (incoming.getCreditsIncluded() > 0) {
            plan.setCreditsIncluded(incoming.getCreditsIncluded());
        }
        if (incoming.getCreditCostOcr() > 0) {
            plan.setCreditCostOcr(incoming.getCreditCostOcr());
        }
        if (incoming.getCreditCostAiImage() > 0) {
            plan.setCreditCostAiImage(incoming.getCreditCostAiImage());
        }
        if (incoming.getFeatures() != null) {
            plan.setFeatures(incoming.getFeatures());
        }
        plan.setActive(incoming.isActive());
        plan.setUpdatedAt(Instant.now());
        return planRepository.save(plan);
    }

    @GetMapping("/admin/users")
    public List<Map<String, Object>> users() {
        requireAdmin();
        return userRepository.findAll().stream().map(user -> {
            Map<String, Object> row = new LinkedHashMap<>(SaasAuthService.profile(user));
            row.put("apiHits", usageEventRepository.countByUserId(user.getId()));
            return row;
        }).toList();
    }

    @PostMapping("/admin/users/{userId}/credits")
    public Map<String, Object> adjustCredits(@PathVariable String userId, @RequestBody Map<String, Object> body) {
        requireAdmin();
        long delta = body.get("delta") instanceof Number n ? n.longValue() : 0L;
        String note = body.get("note") == null ? null : String.valueOf(body.get("note"));
        var event = billingService.adjustCredits(userId, delta, note);
        SaasUserDocument user = userRepository.findById(userId).orElseThrow();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("user", SaasAuthService.profile(user));
        result.put("event", event);
        return result;
    }

    private void requireAdmin() {
        if (!currentSaasUser.require().isAdmin()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "SaaS admin only");
        }
    }
}
