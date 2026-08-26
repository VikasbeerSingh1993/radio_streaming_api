package com.radiostreaming.api.saas.repository;

import com.radiostreaming.api.saas.model.SaasUsageEventDocument;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface SaasUsageEventRepository {
    Optional<SaasUsageEventDocument> findById(String id);

    SaasUsageEventDocument save(SaasUsageEventDocument event);

    List<SaasUsageEventDocument> findAll();

    long count();

    void delete(SaasUsageEventDocument event);

    Page<SaasUsageEventDocument> findByUserIdOrderByCreatedAtDesc(String userId, Pageable pageable);

    long countByUserId(String userId);
}
