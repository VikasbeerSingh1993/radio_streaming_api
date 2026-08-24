package com.radiostreaming.api.controller;

import com.radiostreaming.api.model.AudioLinkDocument;
import com.radiostreaming.api.model.CategoryDocument;
import com.radiostreaming.api.model.EventDocument;
import com.radiostreaming.api.model.StationDocument;
import com.radiostreaming.api.service.RadioCacheService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class RadioApiController {

    private final RadioCacheService radioCacheService;

    public RadioApiController(RadioCacheService radioCacheService) {
        this.radioCacheService = radioCacheService;
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
