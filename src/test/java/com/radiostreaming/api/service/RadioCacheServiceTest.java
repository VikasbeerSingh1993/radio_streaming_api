package com.radiostreaming.api.service;

import com.radiostreaming.api.model.AudioLinkDocument;
import com.radiostreaming.api.model.CategoryDocument;
import com.radiostreaming.api.model.EventDocument;
import com.radiostreaming.api.model.StationDocument;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RadioCacheServiceTest {

    @Mock
    private RadioDataService dataService;

    private RadioCacheService cacheService;
    private AtomicReference<Instant> now;

    @BeforeEach
    void setUp() {
        now = new AtomicReference<>(Instant.parse("2026-08-24T00:00:00Z"));
        Clock clock = new Clock() {
            @Override
            public ZoneOffset getZone() {
                return ZoneOffset.UTC;
            }

            @Override
            public Clock withZone(java.time.ZoneId zone) {
                return this;
            }

            @Override
            public Instant instant() {
                return now.get();
            }
        };
        cacheService = new RadioCacheService(dataService, Duration.ofHours(24), clock);
        stubDatabase();
    }

    @Test
    void loadsFromDatabaseOnceWithinTtl() {
        cacheService.getStations();
        cacheService.getStations();
        cacheService.getCategories();
        cacheService.getEvents();
        cacheService.getAudioLinksByStation("station-1");

        verify(dataService, times(1)).getAllStations();
        verify(dataService, times(1)).getAllCategories();
        verify(dataService, times(1)).getAllEvents();
        verify(dataService, times(1)).getAudioLinksByStation("station-1");
    }

    @Test
    void reloadApiForcesDatabaseHit() {
        cacheService.getStations();
        cacheService.reloadFromDatabase();

        verify(dataService, times(2)).getAllStations();
        verify(dataService, times(2)).getAllCategories();
        verify(dataService, times(2)).getAllEvents();
    }

    @Test
    void reloadsFromDatabaseAfterTtlExpires() {
        cacheService.getStations();
        now.set(now.get().plus(Duration.ofHours(25)));
        cacheService.getStations();

        verify(dataService, times(2)).getAllStations();
    }

    @Test
    void servesAudioLinksFromCache() {
        List<AudioLinkDocument> links = cacheService.getAudioLinksByStation("station-1");
        assertEquals(1, links.size());
        assertEquals("link-1", links.getFirst().getId());
    }

    @Test
    void upsertEventUpdatesCacheWithoutReloadingDatabase() {
        cacheService.getAllEventsForAdmin();
        EventDocument created = new EventDocument();
        created.setId("event-2");
        created.setTitle("Cached Sangat");
        created.setApprovalStatus("approved");

        cacheService.upsertEvent(created);

        List<EventDocument> events = cacheService.getAllEventsForAdmin();
        assertEquals(2, events.size());
        assertEquals("Cached Sangat", events.getFirst().getTitle());
        verify(dataService, times(1)).getAllEvents();
    }

    @Test
    void hidesPendingEventsFromPublicList() {
        EventDocument pending = new EventDocument();
        pending.setTitle("Pending Sangat");
        pending.setApprovalStatus("pending");
        EventDocument approved = new EventDocument();
        approved.setTitle("Approved Sangat");
        approved.setApprovalStatus("approved");
        when(dataService.getAllEvents()).thenReturn(List.of(pending, approved));

        List<EventDocument> publicEvents = cacheService.getEvents();

        assertEquals(1, publicEvents.size());
        assertEquals("Approved Sangat", publicEvents.getFirst().getTitle());
    }

    @Test
    void nearbyEventsFilterByRadius() {
        EventDocument nearby = new EventDocument();
        nearby.setTitle("Amritsar");
        nearby.setLatitude(31.634);
        nearby.setLongitude(74.872);
        nearby.setApprovalStatus("approved");
        EventDocument far = new EventDocument();
        far.setTitle("Delhi");
        far.setLatitude(28.6139);
        far.setLongitude(77.209);
        far.setApprovalStatus("approved");
        when(dataService.getAllEvents()).thenReturn(List.of(nearby, far));

        List<EventDocument> results = cacheService.getNearbyEvents(31.62, 74.876, 50);

        assertEquals(1, results.size());
        assertEquals("Amritsar", results.getFirst().getTitle());
        assertTrue(results.getFirst().getDistanceKm() > 0);
    }

    private void stubDatabase() {
        StationDocument station = new StationDocument();
        station.setId("station-1");
        when(dataService.getAllStations()).thenReturn(List.of(station));
        when(dataService.getAllCategories()).thenReturn(List.of(new CategoryDocument()));
        when(dataService.getAllEvents()).thenReturn(List.of(new EventDocument()));

        AudioLinkDocument link = new AudioLinkDocument();
        link.setId("link-1");
        link.setStationId("station-1");
        when(dataService.getAudioLinksByStation("station-1")).thenReturn(List.of(link));
    }
}
