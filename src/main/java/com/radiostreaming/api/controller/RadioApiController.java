package com.radiostreaming.api.controller;

import com.radiostreaming.api.dto.EventSubmitRequest;
import com.radiostreaming.api.model.AudioLinkDocument;
import com.radiostreaming.api.model.CategoryDocument;
import com.radiostreaming.api.model.EventDocument;
import com.radiostreaming.api.model.StationDocument;
import com.radiostreaming.api.service.AdminCatalogService;
import com.radiostreaming.api.service.RadioCacheService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RadioApiController {

    private final RadioCacheService radioCacheService;
    private final AdminCatalogService adminCatalogService;

    public RadioApiController(RadioCacheService radioCacheService, AdminCatalogService adminCatalogService) {
        this.radioCacheService = radioCacheService;
        this.adminCatalogService = adminCatalogService;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of(
                "status", "OK",
                "message", "Radio Streaming Spring Boot API is running"
        );
    }

    @GetMapping("/cache/status")
    public Map<String, Object> cacheStatus() {
        return radioCacheService.status();
    }

    @PostMapping("/cache/reload")
    public Map<String, Object> reloadCache() {
        return radioCacheService.reloadFromDatabase();
    }

    @GetMapping("/stations")
    public List<StationDocument> getStations() {
        return radioCacheService.getStations();
    }

    @GetMapping("/categories")
    public List<CategoryDocument> getCategories() {
        return radioCacheService.getCategories();
    }

    @GetMapping("/events")
    public List<EventDocument> getEvents() {
        return radioCacheService.getEvents();
    }

    @GetMapping("/events/nearby")
    public List<EventDocument> getNearbyEvents(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "50") double radiusKm) {
        return radioCacheService.getNearbyEvents(lat, lng, radiusKm);
    }

    @PostMapping("/events/submit")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> submitEvent(@Valid @RequestBody EventSubmitRequest request) {
        EventDocument saved = adminCatalogService.submitEvent(request);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("success", true);
        body.put("id", saved.getId());
        body.put("approvalStatus", saved.getApprovalStatus());
        body.put("message", "Event submitted for admin approval");
        return body;
    }

    @GetMapping("/audio-links/station/{stationId}")
    public List<AudioLinkDocument> getAudioLinksByStation(@PathVariable String stationId) {
        return radioCacheService.getAudioLinksByStation(stationId);
    }

    @PutMapping("/audio-links/{linkId}/played")
    public ResponseEntity<Map<String, Object>> updateLinkPlayedFlag(
            @PathVariable String linkId,
            @RequestBody Map<String, Boolean> body) {
        boolean played = body.getOrDefault("played", true);
        boolean updated = radioCacheService.updateLinkPlayedFlag(linkId, played);
        if (!updated) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("success", true, "played", played));
    }

    @PostMapping("/audio-links/station/{stationId}/reset")
    public ResponseEntity<Map<String, Object>> resetStationPlayedFlags(@PathVariable String stationId) {
        long affected = radioCacheService.resetStationPlayedFlags(stationId);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "All links reset to unplayed",
                "affectedRows", affected
        ));
    }
}
