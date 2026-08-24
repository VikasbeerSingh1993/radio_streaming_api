package com.radiostreaming.api.repository;

import com.radiostreaming.api.model.CountryDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CountryRepository extends MongoRepository<CountryDocument, String> {
}
