package com.radiostreaming.api.saas.repository;

import com.radiostreaming.api.saas.model.SaasUsageEventDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SaasUsageEventRepository extends MongoRepository<SaasUsageEventDocument, String> {
    Page<SaasUsageEventDocument> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
    long countByUserId(String userId);
}
