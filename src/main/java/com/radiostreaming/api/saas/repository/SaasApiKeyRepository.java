package com.radiostreaming.api.saas.repository;

import com.radiostreaming.api.saas.model.SaasApiKeyDocument;

import java.util.List;
import java.util.Optional;

public interface SaasApiKeyRepository {
    Optional<SaasApiKeyDocument> findById(String id);

    SaasApiKeyDocument save(SaasApiKeyDocument key);

    List<SaasApiKeyDocument> findAll();

    long count();

    void delete(SaasApiKeyDocument key);

    List<SaasApiKeyDocument> findByUserIdOrderByCreatedAtDesc(String userId);

    Optional<SaasApiKeyDocument> findByKeyHash(String keyHash);

    Optional<SaasApiKeyDocument> findByIdAndUserId(String id, String userId);
}
