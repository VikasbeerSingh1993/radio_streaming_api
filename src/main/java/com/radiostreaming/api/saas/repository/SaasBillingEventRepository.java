package com.radiostreaming.api.saas.repository;

import com.radiostreaming.api.saas.model.SaasBillingEventDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface SaasBillingEventRepository {
    Optional<SaasBillingEventDocument> findById(String id);

    SaasBillingEventDocument save(SaasBillingEventDocument event);

    List<SaasBillingEventDocument> findAll();

    long count();

    void delete(SaasBillingEventDocument event);

    Page<SaasBillingEventDocument> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);
}
