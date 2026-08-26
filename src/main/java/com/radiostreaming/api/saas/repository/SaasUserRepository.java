package com.radiostreaming.api.saas.repository;

import com.radiostreaming.api.saas.model.SaasUserDocument;

import java.util.List;
import java.util.Optional;

public interface SaasUserRepository {
    Optional<SaasUserDocument> findById(String id);

    SaasUserDocument save(SaasUserDocument user);

    List<SaasUserDocument> findAll();

    long count();

    void delete(SaasUserDocument user);

    Optional<SaasUserDocument> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
