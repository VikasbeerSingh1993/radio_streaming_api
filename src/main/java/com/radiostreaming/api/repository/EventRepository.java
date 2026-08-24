package com.radiostreaming.api.repository;

import com.radiostreaming.api.model.EventDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EventRepository extends MongoRepository<EventDocument, String> {

    List<EventDocument> findAllByOrderByDateAsc();
}
