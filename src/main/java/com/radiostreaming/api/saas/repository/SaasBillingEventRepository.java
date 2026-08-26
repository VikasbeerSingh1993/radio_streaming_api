package com.radiostreaming.api.saas.repository;

import com.radiostreaming.api.saas.model.SaasBillingEventDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;

public interface SaasBillingEventRepository extends MongoRepository<SaasBillingEventDocument, String> {
    Page<SaasBillingEventDocument> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
