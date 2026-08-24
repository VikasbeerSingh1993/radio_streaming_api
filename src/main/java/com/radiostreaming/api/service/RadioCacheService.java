package com.radiostreaming.api.service;

import com.radiostreaming.api.model.AudioLinkDocument;
import com.radiostreaming.api.model.CategoryDocument;
import com.radiostreaming.api.model.EventDocument;
import com.radiostreaming.api.model.StationDocument;
import com.radiostreaming.api.util.GeoUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
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
        return ensureLoaded().events.stream()
                .filter(EventDocument::isListedPublicly)
                .toList();
    }

    public List<EventDocument> getAllEventsForAdmin() {
        return List.copyOf(requireSnapshot().events);
    }

    public List<StationDocument> getStationsForAdmin() {
        return List.copyOf(requireSnapshot().stations);
    }

    public List<CategoryDocument> getCategoriesForAdmin() {
        return List.copyOf(requireSnapshot().categories);
    }

    public List<AudioLinkDocument> getAllAudioLinksForAdmin() {
        List<AudioLinkDocument> links = new ArrayList<>();
        requireSnapshot().audioLinksByStation.values().forEach(links::addAll);
        return links;
    }

    public Map<String, StationDocument> stationsByIdForAdmin() {
        Map<String, StationDocument> map = new LinkedHashMap<>();
        for (StationDocument station : requireSnapshot().stations) {
            map.put(RadioDataService.cleanId(station.getId()), station);
        }
        return map;
    }

    public List<EventDocument> getNearbyEvents(double latitude, double longitude, double radiusKm) {
        double radius = radiusKm <= 0 ? 50 : Math.min(radiusKm, 500);
        return getEvents().stream()
                .filter(event -> event.getLatitude() != null && event.getLongitude() != null)
                .map(event -> {
                    double distance = GeoUtils.haversineKm(
                            latitude, longitude, event.getLatitude(), event.getLongitude());
                    EventDocument copy = new EventDocument();
                    BeanUtils.copyProperties(event, copy);
                    copy.setDistanceKm(Math.round(distance * 10.0) / 10.0);
                    return copy;
                })
                .filter(event -> event.getDistanceKm() <= radius)
                .sorted(Comparator.comparing(EventDocument::getDistanceKm))
                .toList();
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

    public void upsertEvent(EventDocument event) {
        mutate(current -> replaceOrAdd(current.events, event, EventDocument::getId));
    }

    public void removeEvent(String id) {
        mutate(current -> current.events.removeIf(item -> sameId(item.getId(), id)));
    }

    public void upsertStation(StationDocument station) {
        mutate(current -> replaceOrAdd(current.stations, station, StationDocument::getId));
    }

    public void removeStation(String id) {
        mutate(current -> {
            current.stations.removeIf(item -> sameId(item.getId(), id));
            current.audioLinksByStation.remove(RadioDataService.cleanId(id));
        });
    }

    public void upsertCategory(CategoryDocument category) {
        mutate(current -> replaceOrAdd(current.categories, category, CategoryDocument::getId));
    }

    public void removeCategory(String id) {
        mutate(current -> current.categories.removeIf(item -> sameId(item.getId(), id)));
    }

    public void upsertAudioLink(AudioLinkDocument link) {
        mutate(current -> {
            if (link == null || link.getId() == null) {
                return;
            }
            removeLink(current, link.getId());
            String stationId = RadioDataService.cleanId(link.getStationId());
            current.audioLinksByStation
                    .computeIfAbsent(stationId, key -> new ArrayList<>())
                    .add(link);
        });
    }

    public void removeAudioLink(String id) {
        mutate(current -> removeLink(current, id));
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

    /**
     * Admin screens keep serving the last loaded snapshot even after TTL expiry.
     * They only hit MongoDB when {@link #reloadFromDatabase()} is called.
     */
    private Snapshot requireSnapshot() {
        Snapshot current = snapshot;
        if (current != null) {
            return current;
        }
        return loadFromDatabase(false);
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
        body.put("audioLinks", audioLinkCount(current));
        return body;
    }

    private void mutate(java.util.function.Consumer<Snapshot> action) {
        lock.lock();
        try {
            Snapshot current = snapshot;
            if (current == null) {
                return;
            }
            action.accept(current);
        } finally {
            lock.unlock();
        }
    }

    private static void removeLink(Snapshot current, String id) {
        for (List<AudioLinkDocument> links : current.audioLinksByStation.values()) {
            links.removeIf(item -> sameId(item.getId(), id));
        }
    }

    private static <T> void replaceOrAdd(List<T> items, T item, java.util.function.Function<T, String> idFn) {
        if (item == null || idFn.apply(item) == null) {
            return;
        }
        String id = idFn.apply(item);
        for (int i = 0; i < items.size(); i++) {
            if (sameId(idFn.apply(items.get(i)), id)) {
                items.set(i, item);
                return;
            }
        }
        items.add(0, item);
    }

    private static boolean sameId(String left, String right) {
        return RadioDataService.cleanId(left).equals(RadioDataService.cleanId(right));
    }

    private static int audioLinkCount(Snapshot current) {
        int count = 0;
        for (List<AudioLinkDocument> links : current.audioLinksByStation.values()) {
            count += links.size();
        }
        return count;
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
