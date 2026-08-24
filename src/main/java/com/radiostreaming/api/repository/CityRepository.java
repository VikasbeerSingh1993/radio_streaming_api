package com.radiostreaming.api.repository;

import com.radiostreaming.api.model.CityDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CityRepository extends MongoRepository<CityDocument, String> {

    List<CityDocument> findByCountryCodeIgnoreCaseAndStateIgnoreCaseOrderByNameAsc(
            String countryCode,
            String state);
}
