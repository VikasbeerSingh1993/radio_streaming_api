package com.radiostreaming.api.service;

import com.radiostreaming.api.model.AudioLinkDocument;
import com.radiostreaming.api.model.CategoryDocument;
import com.radiostreaming.api.model.EventDocument;
import com.radiostreaming.api.model.StationDocument;
import com.radiostreaming.api.repository.CategoryRepository;
import com.radiostreaming.api.repository.EventRepository;
import com.radiostreaming.api.repository.StationRepository;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;

@Service
public class RadioDataService {

    private final StationRepository stationRepository;
    private final CategoryRepository categoryRepository;
    private final EventRepository eventRepository;
    private final MongoTemplate mongoTemplate;

    public RadioDataService(
            StationRepository stationRepository,
            CategoryRepository categoryRepository,
            EventRepository eventRepository,
            MongoTemplate mongoTemplate) {
        this.stationRepository = stationRepository;
        this.categoryRepository = categoryRepository;
        this.eventRepository = eventRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public List<StationDocument> getAllStations() {
        return stationRepository.findAll();
    }

    public List<CategoryDocument> getAllCategories() {
        List<CategoryDocument> categories = categoryRepository.findAll();
        categories.sort(Comparator.comparing(
                c -> c.getOrder() != null ? c.getOrder() : 0
        ));
        return categories;
    }

    public List<EventDocument> getAllEvents() {
        return eventRepository.findAllByOrderByDateAsc();
    }

    public List<AudioLinkDocument> getAudioLinksByStation(String stationId) {
        String cleanStationId = cleanId(stationId);

        Query stringQuery = new Query(Criteria.where("station_id").is(cleanStationId));
        List<AudioLinkDocument> links = mongoTemplate.find(stringQuery, AudioLinkDocument.class);

        if (links.isEmpty() && ObjectId.isValid(cleanStationId)) {
            Query objectIdQuery = new Query(
                    Criteria.where("station_id").is(new ObjectId(cleanStationId))
            );
            links = mongoTemplate.find(objectIdQuery, AudioLinkDocument.class);
        }

        links.sort(Comparator.comparing(
                l -> l.getSequence() != null ? l.getSequence() : 0
        ));

        for (AudioLinkDocument link : links) {
            normalizeAudioLink(link, cleanStationId);
        }

        return links;
    }

    public boolean updateLinkPlayedFlag(String linkId, boolean played) {
        String cleanLinkId = cleanId(linkId);
        if (!ObjectId.isValid(cleanLinkId)) {
            return false;
        }

        Query query = new Query(Criteria.where("_id").is(new ObjectId(cleanLinkId)));
        Update update = new Update()
                .set("played", played)
                .set("status", played ? "Y" : "N");

        return mongoTemplate.updateFirst(query, update, AudioLinkDocument.class)
                .getMatchedCount() > 0;
    }

    public long resetStationPlayedFlags(String stationId) {
        String cleanStationId = cleanId(stationId);

        Query stringQuery = new Query(Criteria.where("station_id").is(cleanStationId));
        Update update = new Update()
                .set("played", false)
                .set("status", "N");

        long modified = mongoTemplate.updateMulti(stringQuery, update, AudioLinkDocument.class)
                .getModifiedCount();

        if (modified == 0 && ObjectId.isValid(cleanStationId)) {
            Query objectIdQuery = new Query(
                    Criteria.where("station_id").is(new ObjectId(cleanStationId))
            );
            modified = mongoTemplate.updateMulti(objectIdQuery, update, AudioLinkDocument.class)
                    .getModifiedCount();
        }

        return modified;
    }

    private void normalizeAudioLink(AudioLinkDocument link, String fallbackStationId) {
        if (link.getStationId() == null || link.getStationId().isBlank()) {
            link.setStationId(fallbackStationId);
        } else {
            link.setStationId(cleanId(link.getStationId()));
        }

        if (link.getPlayed() == null) {
            link.setPlayed("Y".equalsIgnoreCase(link.getStatus()));
        }
    }

    static String cleanId(String id) {
        if (id == null) {
            return "";
        }
        String trimmed = id.trim();
        if (trimmed.startsWith("ObjectId(\"") && trimmed.endsWith("\")")) {
            return trimmed.substring(10, trimmed.length() - 2);
        }
        return trimmed;
    }
}
