package com.radiostreaming.api.saas.repository;

import com.radiostreaming.api.saas.model.SaasPlanDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface SaasPlanRepository extends MongoRepository<SaasPlanDocument, String> {
    List<SaasPlanDocument> findByActiveTrueOrderBySortOrderAsc();
    Optional<SaasPlanDocument> findByCode(String code);
}
