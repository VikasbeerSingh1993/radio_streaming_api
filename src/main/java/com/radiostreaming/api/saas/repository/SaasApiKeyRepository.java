package com.radiostreaming.api.saas.repository;

import com.radiostreaming.api.saas.model.SaasApiKeyDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SaasApiKeyRepository extends MongoRepository<SaasApiKeyDocument, String> {
    List<SaasApiKeyDocument> findByUserIdOrderByCreatedAtDesc(String userId);
    Optional<SaasApiKeyDocument> findByKeyHash(String keyHash);
    Optional<SaasApiKeyDocument> findByIdAndUserId(String id, String userId);
}
