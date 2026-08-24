package com.radiostreaming.api.repository;

import com.radiostreaming.api.model.StationDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StationRepository extends MongoRepository<StationDocument, String> {

    List<StationDocument> findAllByOrderByCategoryAsc();
}
