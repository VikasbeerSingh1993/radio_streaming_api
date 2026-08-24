package com.radiostreaming.api.repository;

import com.radiostreaming.api.model.CredentialDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CredentialRepository extends MongoRepository<CredentialDocument, String> {

    Optional<CredentialDocument> findByTypeIgnoreCase(String type);
}
