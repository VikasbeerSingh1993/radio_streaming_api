package com.radiostreaming.api.repository;

import com.radiostreaming.api.model.EventSubmissionDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface EventSubmissionRepository extends MongoRepository<EventSubmissionDocument, String> {
}
