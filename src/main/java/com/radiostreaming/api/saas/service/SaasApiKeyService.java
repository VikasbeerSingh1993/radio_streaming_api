package com.radiostreaming.api.saas.service;

import com.radiostreaming.api.saas.dto.CreateApiKeyRequest;
import com.radiostreaming.api.saas.model.SaasApiKeyDocument;
import com.radiostreaming.api.saas.repository.SaasApiKeyRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class SaasApiKeyService {

    private final SaasApiKeyRepository apiKeyRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public SaasApiKeyService(SaasApiKeyRepository apiKeyRepository) {
        this.apiKeyRepository = apiKeyRepository;
    }

    /**
     * Creates a key. The full plaintext is returned once only.
     */
    public Map<String, Object> create(String userId, CreateApiKeyRequest request) {
        byte[] bytes = new byte[24];
        secureRandom.nextBytes(bytes);
        String secret = HexFormat.of().formatHex(bytes);
        String rawKey = "dbsk_" + secret;
        Instant now = Instant.now();

        SaasApiKeyDocument doc = new SaasApiKeyDocument();
        doc.setUserId(userId);
        doc.setName(request.getName().trim());
        doc.setKeyPrefix(rawKey.substring(0, Math.min(12, rawKey.length())));
        doc.setKeyHash(CreditMeteringService.hashKey(rawKey));
        doc.setRevoked(false);
        doc.setHitCount(0);
        doc.setCreatedAt(now);
        SaasApiKeyDocument saved = apiKeyRepository.save(doc);

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", saved.getId());
        body.put("name", saved.getName());
        body.put("keyPrefix", saved.getKeyPrefix());
        body.put("apiKey", rawKey);
        body.put("createdAt", saved.getCreatedAt());
        body.put("message", "Store this API key now. It will not be shown again.");
        return body;
    }

    public List<SaasApiKeyDocument> list(String userId) {
        return apiKeyRepository.findByUserIdOrderByCreatedAtDesc(userId);
    }

    public void revoke(String userId, String keyId) {
        SaasApiKeyDocument key = apiKeyRepository.findByIdAndUserId(keyId, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "API key not found"));
        key.setRevoked(true);
        apiKeyRepository.save(key);
    }
}
