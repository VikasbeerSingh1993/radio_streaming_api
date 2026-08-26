package com.radiostreaming.api.saas.service;

import com.radiostreaming.api.saas.model.SaasApiKeyDocument;
import com.radiostreaming.api.saas.model.SaasPlanDocument;
import com.radiostreaming.api.saas.model.SaasUsageEventDocument;
import com.radiostreaming.api.saas.model.SaasUserDocument;
import com.radiostreaming.api.saas.repository.SaasApiKeyRepository;
import com.radiostreaming.api.saas.repository.SaasPlanRepository;
import com.radiostreaming.api.saas.repository.SaasUsageEventRepository;
import com.radiostreaming.api.saas.repository.SaasUserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Validates API keys, checks credits, deducts, and logs usage hits.
 */
@Service
public class CreditMeteringService {

    public static final String OP_OCR = "OCR_PUNJABI";
    public static final String OP_AI_IMAGE = "AI_IMAGE";
    public static final String OP_SIKH_HISTORY = "SIKH_HISTORY";
    public static final String OP_GURBANI_AI = "GURBANI_AI";

    private final SaasUserRepository userRepository;
    private final SaasApiKeyRepository apiKeyRepository;
    private final SaasPlanRepository planRepository;
    private final SaasUsageEventRepository usageEventRepository;

    public CreditMeteringService(
            SaasUserRepository userRepository,
            SaasApiKeyRepository apiKeyRepository,
            SaasPlanRepository planRepository,
            SaasUsageEventRepository usageEventRepository) {
        this.userRepository = userRepository;
        this.apiKeyRepository = apiKeyRepository;
        this.planRepository = planRepository;
        this.usageEventRepository = usageEventRepository;
    }

    public record MeteredCall(SaasUserDocument user, SaasApiKeyDocument apiKey, int cost, boolean overage) {}

    /**
     * Prefer API key for external integrations; otherwise charge the signed-in website user.
     */
    public MeteredCall authorizeAndPrepare(String rawApiKey, SaasUserDocument sessionUser, String operation, int units) {
        SaasUserDocument user;
        SaasApiKeyDocument key = null;
        if (rawApiKey != null && !rawApiKey.isBlank()) {
            String hash = hashKey(rawApiKey.trim());
            key = apiKeyRepository.findByKeyHash(hash)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid API key"));
            if (key.isRevoked()) {
                throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "API key revoked");
            }
            user = userRepository.findById(key.getUserId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "User not found for API key"));
        } else if (sessionUser != null) {
            user = userRepository.findById(sessionUser.getId())
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in"));
        } else {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Sign in on the website, or send an API key (X-API-Key) for external use");
        }
        if (!user.isEnabled()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Account disabled");
        }

        int unitCost = resolveUnitCost(user, operation);
        int totalCost = Math.max(1, units) * unitCost;
        boolean overage = false;
        if (user.getCreditsRemaining() < totalCost) {
            boolean allow = switch (operation) {
                case OP_OCR -> user.isAllowOcrOverage();
                case OP_AI_IMAGE -> user.isAllowAiImageOverage();
                default -> false;
            };
            if (!allow) {
                throw new ResponseStatusException(HttpStatus.PAYMENT_REQUIRED,
                        "Insufficient credits. Remaining: " + user.getCreditsRemaining()
                                + ", required: " + totalCost
                                + ". Enable additional usage or purchase a plan.");
            }
            overage = true;
        }
        return new MeteredCall(user, key, totalCost, overage);
    }

    public MeteredCall authorizeAndPrepare(String rawApiKey, String operation, int units) {
        return authorizeAndPrepare(rawApiKey, null, operation, units);
    }

    public Map<String, Object> commit(MeteredCall call, String operation, Map<String, Object> metadata) {
        SaasUserDocument user = userRepository.findById(call.user().getId()).orElseThrow();
        Instant now = Instant.now();
        long remaining = user.getCreditsRemaining() - call.cost();
        if (remaining < 0) {
            remaining = 0;
        }
        user.setCreditsRemaining(remaining);
        user.setCreditsUsed(user.getCreditsUsed() + call.cost());
        user.setUpdatedAt(now);
        userRepository.save(user);

        SaasApiKeyDocument key = call.apiKey();
        if (key != null) {
            key.setLastUsedAt(now);
            key.setHitCount(key.getHitCount() + 1);
            apiKeyRepository.save(key);
        }

        SaasUsageEventDocument event = new SaasUsageEventDocument();
        event.setUserId(user.getId());
        event.setApiKeyId(key == null ? null : key.getId());
        event.setOperation(operation);
        event.setCreditsCharged(call.cost());
        event.setOverage(call.overage());
        event.setStatus("ok");
        event.setMetadata(metadata);
        event.setCreatedAt(now);
        usageEventRepository.save(event);

        Map<String, Object> meter = new LinkedHashMap<>();
        meter.put("creditsCharged", call.cost());
        meter.put("creditsRemaining", user.getCreditsRemaining());
        meter.put("creditsUsed", user.getCreditsUsed());
        meter.put("overage", call.overage());
        meter.put("usageEventId", event.getId());
        return meter;
    }

    public int resolveUnitCost(SaasUserDocument user, String operation) {
        if (user.getPlanId() != null) {
            SaasPlanDocument plan = planRepository.findById(user.getPlanId()).orElse(null);
            if (plan != null) {
                return switch (operation) {
                    case OP_OCR -> plan.getCreditCostOcr() > 0 ? plan.getCreditCostOcr() : 5;
                    case OP_AI_IMAGE -> plan.getCreditCostAiImage() > 0 ? plan.getCreditCostAiImage() : 10;
                    case OP_SIKH_HISTORY -> plan.getCreditCostSikhHistory() > 0 ? plan.getCreditCostSikhHistory() : 2;
                    case OP_GURBANI_AI -> plan.getCreditCostGurbaniAi() > 0 ? plan.getCreditCostGurbaniAi() : 3;
                    default -> 5;
                };
            }
        }
        return switch (operation) {
            case OP_OCR -> 5;
            case OP_AI_IMAGE -> 10;
            case OP_SIKH_HISTORY -> 2;
            case OP_GURBANI_AI -> 3;
            default -> 5;
        };
    }

    public static String hashKey(String raw) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException(ex);
        }
    }
}
