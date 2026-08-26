package com.radiostreaming.api.saas.controller;

import com.radiostreaming.api.billing.BillingService;
import com.radiostreaming.api.saas.dto.CreateApiKeyRequest;
import com.radiostreaming.api.saas.model.SaasApiKeyDocument;
import com.radiostreaming.api.saas.model.SaasBillingEventDocument;
import com.radiostreaming.api.saas.model.SaasUsageEventDocument;
import com.radiostreaming.api.saas.repository.SaasUsageEventRepository;
import com.radiostreaming.api.saas.security.CurrentSaasUser;
import com.radiostreaming.api.saas.service.SaasApiKeyService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/saas")
public class SaasAccountController {

    private final CurrentSaasUser currentSaasUser;
    private final SaasApiKeyService apiKeyService;
    private final BillingService billingService;
    private final SaasUsageEventRepository usageEventRepository;

    public SaasAccountController(
            CurrentSaasUser currentSaasUser,
            SaasApiKeyService apiKeyService,
            BillingService billingService,
            SaasUsageEventRepository usageEventRepository) {
        this.currentSaasUser = currentSaasUser;
        this.apiKeyService = apiKeyService;
        this.billingService = billingService;
        this.usageEventRepository = usageEventRepository;
    }

    @PostMapping("/api-keys")
    public Map<String, Object> createKey(@Valid @RequestBody CreateApiKeyRequest request) {
        return apiKeyService.create(currentSaasUser.require().getUserId(), request);
    }

    @GetMapping("/api-keys")
    public List<Map<String, Object>> listKeys() {
        return apiKeyService.list(currentSaasUser.require().getUserId()).stream()
                .map(this::maskKey)
                .toList();
    }

    @DeleteMapping("/api-keys/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revokeKey(@PathVariable String id) {
        apiKeyService.revoke(currentSaasUser.require().getUserId(), id);
    }

    @GetMapping("/usage")
    public Map<String, Object> usage(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        String userId = currentSaasUser.require().getUserId();
        Page<SaasUsageEventDocument> events = usageEventRepository.findByUserIdOrderByCreatedAtDesc(
                userId, PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size))));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("totalHits", usageEventRepository.countByUserId(userId));
        body.put("page", events.getNumber());
        body.put("size", events.getSize());
        body.put("totalElements", events.getTotalElements());
        body.put("items", events.getContent());
        return body;
    }

    @GetMapping("/billing")
    public Page<SaasBillingEventDocument> billing(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return billingService.history(currentSaasUser.require().getUserId(), page, size);
    }

    @PostMapping("/plans/{planId}/purchase")
    public Map<String, Object> purchase(@PathVariable String planId) {
        return billingService.purchasePlan(currentSaasUser.require().getUserId(), planId);
    }

    private Map<String, Object> maskKey(SaasApiKeyDocument key) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", key.getId());
        row.put("name", key.getName());
        row.put("keyPrefix", key.getKeyPrefix());
        row.put("revoked", key.isRevoked());
        row.put("hitCount", key.getHitCount());
        row.put("lastUsedAt", key.getLastUsedAt());
        row.put("createdAt", key.getCreatedAt());
        return row;
    }
}
