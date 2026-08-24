package com.radiostreaming.api.repository;

import com.radiostreaming.api.model.StateDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface StateRepository extends MongoRepository<StateDocument, String> {

    List<StateDocument> findByCountryCodeIgnoreCaseOrderByNameAsc(String countryCode);
}
