package com.radiostreaming.api.saas.repository;

import com.radiostreaming.api.saas.model.SaasUserDocument;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Optional;

public interface SaasUserRepository extends MongoRepository<SaasUserDocument, String> {
    Optional<SaasUserDocument> findByEmailIgnoreCase(String email);
    boolean existsByEmailIgnoreCase(String email);
}
