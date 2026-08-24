package com.radiostreaming.api.controller;

import com.radiostreaming.api.model.AudioLinkDocument;
import com.radiostreaming.api.model.CategoryDocument;
import com.radiostreaming.api.model.EventDocument;
import com.radiostreaming.api.model.StationDocument;
import com.radiostreaming.api.service.EventSubmissionService;
import com.radiostreaming.api.service.RadioCacheService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = RadioApiController.class)
@AutoConfigureMockMvc(addFilters = false)
class RadioApiControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RadioCacheService radioCacheService;

    @MockitoBean
    private EventSubmissionService eventSubmissionService;

    @Test
    void healthReturnsOk() throws Exception {
        mockMvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OK"));
    }

    @Test
    void stationsReturnsList() throws Exception {
        StationDocument station = new StationDocument();
        station.setId("507f1f77bcf86cd799439011");
        station.setCategory("live_kirtan");
        station.setType("radio");
        station.setTranslations(Map.of("en", Map.of("name", "Test Station")));
        station.setCreatedAt(Instant.parse("2026-01-01T00:00:00Z"));

        when(radioCacheService.getStations()).thenReturn(List.of(station));

        mockMvc.perform(get("/api/stations"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]._id").value("507f1f77bcf86cd799439011"))
                .andExpect(jsonPath("$[0].category").value("live_kirtan"))
                .andExpect(jsonPath("$[0].translations.en.name").value("Test Station"));
    }

    @Test
    void categoriesReturnsList() throws Exception {
        CategoryDocument category = new CategoryDocument();
        category.setId("cat1");
        category.setCategory("live_kirtan");
        category.setOrder(1);
        category.setTranslations(Map.of("en", Map.of("name", "Live Kirtan")));

        when(radioCacheService.getCategories()).thenReturn(List.of(category));

        mockMvc.perform(get("/api/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]._id").value("cat1"))
                .andExpect(jsonPath("$[0].category").value("live_kirtan"))
                .andExpect(jsonPath("$[0].translations.en.name").value("Live Kirtan"));
    }

    @Test
    void eventsReturnsListWithOrganizerAndVenue() throws Exception {
        EventDocument event = new EventDocument();
        event.setId("evt1");
        event.setTitle("Kirtan Night");
        event.setDate(Instant.parse("2026-09-01T18:00:00Z"));
        event.setEndDate(Instant.parse("2026-09-01T21:00:00Z"));
        event.setCity("Amritsar");
        event.setAddress("Golden Temple Hall");
        event.setOrganizedBy("Divine Bliss Team");
        event.setStatus("scheduled");

        when(radioCacheService.getEvents()).thenReturn(List.of(event));

        mockMvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]._id").value("evt1"))
                .andExpect(jsonPath("$[0].title").value("Kirtan Night"))
                .andExpect(jsonPath("$[0].address").value("Golden Temple Hall"))
                .andExpect(jsonPath("$[0].organizedBy").value("Divine Bliss Team"));
    }

    @Test
    void audioLinksByStationReturnsList() throws Exception {
        AudioLinkDocument link = new AudioLinkDocument();
        link.setId("link1");
        link.setStationId("station1");
        link.setUrl("https://example.com/track.mp3");
        link.setSequence(1);
        link.setPlayed(false);

        when(radioCacheService.getAudioLinksByStation("station1")).thenReturn(List.of(link));

        mockMvc.perform(get("/api/audio-links/station/station1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]._id").value("link1"))
                .andExpect(jsonPath("$[0].station_id").value("station1"))
                .andExpect(jsonPath("$[0].played").value(false));
    }

    @Test
    void updatePlayedFlagReturnsOk() throws Exception {
        when(radioCacheService.updateLinkPlayedFlag("link1", true)).thenReturn(true);

        mockMvc.perform(put("/api/audio-links/link1/played")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"played\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.played").value(true));
    }

    @Test
    void updatePlayedFlagReturnsNotFound() throws Exception {
        when(radioCacheService.updateLinkPlayedFlag("missing", true)).thenReturn(false);

        mockMvc.perform(put("/api/audio-links/missing/played")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"played\":true}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void resetStationPlayedFlagsReturnsOk() throws Exception {
        when(radioCacheService.resetStationPlayedFlags("station1")).thenReturn(4L);

        mockMvc.perform(post("/api/audio-links/station/station1/reset"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.affectedRows").value(4));
    }

    @Test
    void reloadCacheReturnsStatus() throws Exception {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("source", "database");
        status.put("stations", 3);
        status.put("categories", 2);
        status.put("events", 2);
        status.put("audioLinks", 65);
        when(radioCacheService.reloadFromDatabase()).thenReturn(status);

        mockMvc.perform(post("/api/cache/reload"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("database"))
                .andExpect(jsonPath("$.stations").value(3))
                .andExpect(jsonPath("$.audioLinks").value(65));
    }

    @Test
    void nearbyEventsReturnsList() throws Exception {
        EventDocument event = new EventDocument();
        event.setId("evt-near");
        event.setTitle("Local Kirtan");
        event.setDistanceKm(2.4);

        when(radioCacheService.getNearbyEvents(31.62, 74.876, 50)).thenReturn(List.of(event));

        mockMvc.perform(get("/api/events/nearby")
                        .param("lat", "31.62")
                        .param("lng", "74.876"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]._id").value("evt-near"))
                .andExpect(jsonPath("$[0].distanceKm").value(2.4));
    }

    @Test
    void startEventSubmitReturnsOtpChallenge() throws Exception {
        when(eventSubmissionService.start(any())).thenReturn(Map.of(
                "success", true,
                "submissionId", "sub-1",
                "email", "a***n@example.com",
                "message", "We sent a verification code to your email."
        ));

        mockMvc.perform(post("/api/events/submit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "title": "Sangat",
                                  "date": "2026-09-01T10:00:00Z",
                                  "city": "Amritsar",
                                  "username": "aman",
                                  "submitterEmail": "aman@example.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.submissionId").value("sub-1"))
                .andExpect(jsonPath("$.email").value("a***n@example.com"));
    }

    @Test
    void verifyEventSubmitCreatesPendingEvent() throws Exception {
        EventDocument saved = new EventDocument();
        saved.setId("new-event");
        saved.setApprovalStatus("pending");
        when(eventSubmissionService.verify("sub-1", "123456")).thenReturn(saved);

        mockMvc.perform(post("/api/events/submit/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "submissionId": "sub-1",
                                  "otp": "123456"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.id").value("new-event"))
                .andExpect(jsonPath("$.approvalStatus").value("pending"));
    }
}
