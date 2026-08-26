package com.radiostreaming.api.saas.repository;

import com.radiostreaming.api.saas.model.SaasPendingRegistrationDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SaasPendingRegistrationRepository extends MongoRepository<SaasPendingRegistrationDocument, String> {
    Optional<SaasPendingRegistrationDocument> findByEmailIgnoreCase(String email);
    void deleteByEmailIgnoreCase(String email);
}
