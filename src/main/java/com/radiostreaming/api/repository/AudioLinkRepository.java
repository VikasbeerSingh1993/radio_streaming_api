package com.radiostreaming.api.repository;

import com.radiostreaming.api.model.AudioLinkDocument;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AudioLinkRepository extends MongoRepository<AudioLinkDocument, String> {

    List<AudioLinkDocument> findByStationIdOrderBySequenceAsc(String stationId);

    void deleteByStationId(String stationId);
}
