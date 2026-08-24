package com.radiostreaming.api.service;

import com.radiostreaming.api.model.AudioLinkDocument;
import com.radiostreaming.api.model.CategoryDocument;
import com.radiostreaming.api.model.EventDocument;
import com.radiostreaming.api.model.StationDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Second-level in-memory cache in front of MongoDB.
 * Reads come from cache. Mongo is queried once per TTL (default 24 hours)
 * or when {@link #reloadFromDatabase()} is called.
 */
@Service
public class RadioCacheService {

    private static final Logger log = LoggerFactory.getLogger(RadioCacheService.class);

    private final RadioDataService dataService;
    private final Duration ttl;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile Snapshot snapshot;

    @Autowired
    public RadioCacheService(
            RadioDataService dataService,
            @Value("${app.cache.ttl:24h}") Duration ttl) {
        this(dataService, ttl, Clock.systemUTC());
    }

    RadioCacheService(RadioDataService dataService, Duration ttl, Clock clock) {
        this.dataService = dataService;
        this.ttl = ttl;
        this.clock = clock;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void warmUp() {
        try {
            ensureLoaded();
            log.info("Radio cache warmed from MongoDB");
        } catch (Exception ex) {
            log.warn("Cache warm-up failed; will load on first request", ex);
        }
    }

    public List<StationDocument> getStations() {
        return List.copyOf(ensureLoaded().stations);
    }

    public List<CategoryDocument> getCategories() {
        return List.copyOf(ensureLoaded().categories);
    }

    public List<EventDocument> getEvents() {
        return List.copyOf(ensureLoaded().events);
    }

    public List<AudioLinkDocument> getAudioLinksByStation(String stationId) {
        String key = RadioDataService.cleanId(stationId);
        List<AudioLinkDocument> links = ensureLoaded().audioLinksByStation.get(key);
        return links == null ? List.of() : List.copyOf(links);
    }

    public boolean updateLinkPlayedFlag(String linkId, boolean played) {
        boolean updated = dataService.updateLinkPlayedFlag(linkId, played);
        if (updated) {
            applyPlayedToCache(RadioDataService.cleanId(linkId), played);
        }
        return updated;
    }

    public long resetStationPlayedFlags(String stationId) {
        long affected = dataService.resetStationPlayedFlags(stationId);
        String key = RadioDataService.cleanId(stationId);
        Snapshot current = snapshot;
        if (current != null) {
            List<AudioLinkDocument> links = current.audioLinksByStation.get(key);
            if (links != null) {
                for (AudioLinkDocument link : links) {
                    link.setPlayed(false);
                    link.setStatus("N");
                }
            }
        }
        return affected;
    }

    public Map<String, Object> reloadFromDatabase() {
        Snapshot loaded = loadFromDatabase(true);
        return statusOf(loaded, "database");
    }

    public Map<String, Object> status() {
        Snapshot current = snapshot;
        if (current == null) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("source", "empty");
            empty.put("ttl", ttl.toString());
            return empty;
        }
        return statusOf(current, isExpired(current) ? "expired" : "cache");
    }

    private Snapshot ensureLoaded() {
        Snapshot current = snapshot;
        if (isValid(current)) {
            return current;
        }
        return loadFromDatabase(false);
    }

    private Snapshot loadFromDatabase(boolean force) {
        lock.lock();
        try {
            Snapshot current = snapshot;
            if (!force && isValid(current)) {
                return current;
            }
            log.info("Loading stations, categories, events, and audio links from MongoDB");
            List<StationDocument> stations = new ArrayList<>(dataService.getAllStations());
            List<CategoryDocument> categories = new ArrayList<>(dataService.getAllCategories());
            List<EventDocument> events = new ArrayList<>(dataService.getAllEvents());

            Map<String, List<AudioLinkDocument>> linksByStation = new ConcurrentHashMap<>();
            int linkCount = 0;
            for (StationDocument station : stations) {
                String stationId = RadioDataService.cleanId(station.getId());
                List<AudioLinkDocument> links =
                        new ArrayList<>(dataService.getAudioLinksByStation(stationId));
                linksByStation.put(stationId, links);
                linkCount += links.size();
            }

            Snapshot loaded = new Snapshot(
                    stations,
                    categories,
                    events,
                    linksByStation,
                    clock.instant(),
                    linkCount
            );
            snapshot = loaded;
            log.info(
                    "Cache loaded: {} stations, {} categories, {} events, {} audio links",
                    stations.size(),
                    categories.size(),
                    events.size(),
                    linkCount
            );
            return loaded;
        } finally {
            lock.unlock();
        }
    }

    private boolean isValid(Snapshot current) {
        return current != null && !isExpired(current);
    }

    private boolean isExpired(Snapshot current) {
        return !clock.instant().isBefore(current.loadedAt.plus(ttl));
    }

    private void applyPlayedToCache(String linkId, boolean played) {
        Snapshot current = snapshot;
        if (current == null) {
            return;
        }
        for (List<AudioLinkDocument> links : current.audioLinksByStation.values()) {
            for (AudioLinkDocument link : links) {
                if (linkId.equals(RadioDataService.cleanId(link.getId()))) {
                    link.setPlayed(played);
                    link.setStatus(played ? "Y" : "N");
                    return;
                }
            }
        }
    }

    private Map<String, Object> statusOf(Snapshot current, String source) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("source", source);
        body.put("loadedAt", current.loadedAt.toString());
        body.put("expiresAt", current.loadedAt.plus(ttl).toString());
        body.put("ttl", ttl.toString());
        body.put("stations", current.stations.size());
        body.put("categories", current.categories.size());
        body.put("events", current.events.size());
        body.put("audioLinks", current.audioLinkCount);
        return body;
    }

    private record Snapshot(
            List<StationDocument> stations,
            List<CategoryDocument> categories,
            List<EventDocument> events,
            Map<String, List<AudioLinkDocument>> audioLinksByStation,
            Instant loadedAt,
            int audioLinkCount
    ) {
    }
}
