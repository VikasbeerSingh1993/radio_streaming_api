package com.radiostreaming.api.billing;

import com.radiostreaming.api.saas.model.SaasBillingEventDocument;
import com.radiostreaming.api.saas.model.SaasPlanDocument;
import com.radiostreaming.api.saas.model.SaasUserDocument;
import com.radiostreaming.api.saas.repository.SaasBillingEventRepository;
import com.radiostreaming.api.saas.repository.SaasPlanRepository;
import com.radiostreaming.api.saas.repository.SaasUserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Plan purchase and billing history (Mongo saas_billing_events).
 * Payment gateway not wired — purchases credit the account immediately for testing.
 */
@Service
public class BillingService {

    private final SaasPlanRepository planRepository;
    private final SaasUserRepository userRepository;
    private final SaasBillingEventRepository billingEventRepository;

    public BillingService(
            SaasPlanRepository planRepository,
            SaasUserRepository userRepository,
            SaasBillingEventRepository billingEventRepository) {
        this.planRepository = planRepository;
        this.userRepository = userRepository;
        this.billingEventRepository = billingEventRepository;
    }

    public Map<String, Object> purchasePlan(String userId, String planId) {
        SaasPlanDocument plan = planRepository.findById(planId)
                .filter(SaasPlanDocument::isActive)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Plan not found"));
        SaasUserDocument user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));

        Instant now = Instant.now();
        user.setPlanId(plan.getId());
        user.setPlanName(plan.getName());
        user.setCreditsRemaining(user.getCreditsRemaining() + plan.getCreditsIncluded());
        user.setCreditsPending(Math.max(0, user.getCreditsPending() - plan.getCreditsIncluded()));
        user.setUpdatedAt(now);
        userRepository.save(user);

        SaasBillingEventDocument event = new SaasBillingEventDocument();
        event.setUserId(userId);
        event.setPlanId(plan.getId());
        event.setPlanName(plan.getName());
        event.setType("PURCHASE");
        event.setAmountCents(plan.getPriceCents());
        event.setCreditsAdded(plan.getCreditsIncluded());
        event.setStatus("completed");
        event.setNote("Demo purchase — payment provider not connected; credits applied immediately.");
        event.setCreatedAt(now);
        billingEventRepository.save(event);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("plan", plan);
        body.put("creditsRemaining", user.getCreditsRemaining());
        body.put("billingEventId", event.getId());
        body.put("message", "Plan purchased (demo). Credits added to your account.");
        return body;
    }

    public Page<SaasBillingEventDocument> history(String userId, int page, int size) {
        return billingEventRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
    }

    public SaasBillingEventDocument adjustCredits(String userId, long delta, String note) {
        SaasUserDocument user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "User not found"));
        Instant now = Instant.now();
        user.setCreditsRemaining(Math.max(0, user.getCreditsRemaining() + delta));
        if (delta < 0) {
            user.setCreditsUsed(user.getCreditsUsed() + Math.abs(delta));
        }
        user.setUpdatedAt(now);
        userRepository.save(user);

        SaasBillingEventDocument event = new SaasBillingEventDocument();
        event.setUserId(userId);
        event.setType("ADJUSTMENT");
        event.setAmountCents(0);
        event.setCreditsAdded(delta);
        event.setStatus("completed");
        event.setNote(note != null ? note : "Admin adjustment");
        event.setCreatedAt(now);
        return billingEventRepository.save(event);
    }
}
