package com.radiostreaming.api.saas.repository;

import com.radiostreaming.api.saas.model.SaasPendingRegistrationDocument;

import java.util.Optional;

public interface SaasPendingRegistrationRepository {
    Optional<SaasPendingRegistrationDocument> findById(String id);

    SaasPendingRegistrationDocument save(SaasPendingRegistrationDocument pending);

    void delete(SaasPendingRegistrationDocument pending);

    Optional<SaasPendingRegistrationDocument> findByEmailIgnoreCase(String email);

    void deleteByEmailIgnoreCase(String email);
}
