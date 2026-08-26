package com.radiostreaming.api.saas.repository;

import com.radiostreaming.api.saas.model.SaasPlanDocument;

import java.util.List;
import java.util.Optional;

public interface SaasPlanRepository {
    Optional<SaasPlanDocument> findById(String id);

    SaasPlanDocument save(SaasPlanDocument plan);

    List<SaasPlanDocument> findAll();

    long count();

    void delete(SaasPlanDocument plan);

    List<SaasPlanDocument> findByActiveTrueOrderBySortOrderAsc();

    Optional<SaasPlanDocument> findByCode(String code);
}
