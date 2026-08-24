package com.radiostreaming.api.repository;

import com.radiostreaming.api.model.CategoryDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends MongoRepository<CategoryDocument, String> {

    List<CategoryDocument> findAllByOrderByOrderAsc();
}
