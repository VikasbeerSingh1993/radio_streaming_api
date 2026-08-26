package com.radiostreaming.api.controller;

import com.radiostreaming.api.dto.GurbaniSearchHit;
import com.radiostreaming.api.dto.GurbaniSearchPage;
import com.radiostreaming.api.service.GurbaniSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Shabad Seva Gurbani + AI contracts.
 * Gurbani search / ang / shabad / sources read MySQL {@code bani_search} only.
 * Stations, events, and audio catalog remain on MongoDB (see RadioApiController).
 * AI/voice/live-attach endpoints are stubs until ASR is wired server-side.
 */
@RestController
@RequestMapping("/api/v1")
public class ShabadSevaApiController {

    private final GurbaniSearchService gurbaniSearchService;

    public ShabadSevaApiController(GurbaniSearchService gurbaniSearchService) {
        this.gurbaniSearchService = gurbaniSearchService;
    }

    @GetMapping("/gurbani/search")
    public GurbaniSearchPage searchGurbani(
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "word") String mode,
            @RequestParam(defaultValue = "all") String source,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return gurbaniSearchService.search(q, mode, source, page, size);
    }

    @GetMapping("/gurbani/angs/{ang}")
    public Map<String, Object> getAng(
            @PathVariable int ang,
            @RequestParam(defaultValue = "G") String source) {
        return gurbaniSearchService.getAng(ang, source);
    }

    @GetMapping("/gurbani/shabad/{id}")
    public Map<String, Object> getShabad(@PathVariable long id) {
        return gurbaniSearchService.getShabad(id);
    }

    @GetMapping("/gurbani/sources")
    public Map<String, Object> sources() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", gurbaniSearchService.listSources());
        body.put("available", gurbaniSearchService.isAvailable());
        return body;
    }

    @GetMapping("/gurbani/health")
    public Map<String, Object> gurbaniHealth() {
        Map<String, Object> body = new LinkedHashMap<>();
        boolean ok = gurbaniSearchService.isAvailable();
        body.put("mysql", ok ? "up" : "down");
        body.put("database", "bani_search");
        body.put("available", ok);
        return body;
    }

    @GetMapping("/hukamnama/today")
    public Map<String, Object> hukamnamaToday() {
        Map<String, Object> body = gurbaniSearchService.getAng(1, "G");
        body.put("label", "Hukamnama");
        body.put("status", body.getOrDefault("available", false).equals(true) ? "ok" : "unavailable");
        return body;
    }

    /**
     * Stub: accept audio metadata / base64 later; return mock transcript until ASR is plugged in.
     */
    @PostMapping("/ai/gurbani/transcribe")
    public Map<String, Object> transcribe(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        String hint = body == null ? "" : String.valueOf(body.getOrDefault("hint", ""));
        if (hint.isBlank() || "null".equals(hint)) {
            hint = body == null ? "" : String.valueOf(body.getOrDefault("transcriptHint", "ਮਨ ਤੂੰ ਜੋਤਿ"));
        }
        result.put("transcript", hint);
        result.put("confidence", 0.42);
        result.put("language", "pa");
        result.put("status", "stub");
        result.put("message", "ASR model not configured; returning placeholder transcript for UI wiring.");
        return result;
    }

    /**
     * Stub: ranked Gurbani matches from text (or post-ASR transcript). Uses real MySQL search when available.
     */
    @PostMapping("/ai/gurbani/search")
    public Map<String, Object> aiSearch(@RequestBody(required = false) Map<String, Object> body) {
        String query = "";
        String mode = "word";
        String source = "all";
        if (body != null) {
            query = String.valueOf(body.getOrDefault("query", body.getOrDefault("transcript", "")));
            if ("null".equals(query)) {
                query = "";
            }
            mode = String.valueOf(body.getOrDefault("mode", "word"));
            source = String.valueOf(body.getOrDefault("source", "all"));
        }
        GurbaniSearchPage page = gurbaniSearchService.search(query, mode, source, 0, 10);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("query", query);
        result.put("status", page.isAvailable() ? "ok" : "mock");
        result.put("available", page.isAvailable());
        List<Map<String, Object>> matches = new ArrayList<>();
        if (page.isAvailable() && !page.getItems().isEmpty()) {
            double base = 0.92;
            for (GurbaniSearchHit hit : page.getItems()) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("verseId", hit.getVerseId());
                m.put("shabadId", hit.getShabadId());
                m.put("title", hit.getUnicode() != null ? hit.getUnicode() : hit.getGurmukhi());
                m.put("snippet", hit.getTranslation() != null ? hit.getTranslation() : hit.getTransliteration());
                m.put("ang", hit.getAng() != null ? hit.getAng() : hit.getPageNo());
                m.put("sourceCode", hit.getSourceCode());
                m.put("confidence", Math.max(0.55, base));
                m.put("gurmukhi", hit.getGurmukhi());
                m.put("unicode", hit.getUnicode());
                matches.add(m);
                base -= 0.04;
            }
        } else {
            matches.add(sampleAiMatch(query));
        }
        result.put("matches", matches);
        return result;
    }

    /**
     * Live Kirtan attach: client streams / batches audio while a live station plays.
     * Stub until real ASR; echoes hint and runs text search when provided.
     */
    @PostMapping("/ai/gurbani/live-attach")
    public Map<String, Object> liveAttach(@RequestBody(required = false) Map<String, Object> body) {
        Map<String, Object> result = new LinkedHashMap<>();
        String stationId = body == null ? "" : String.valueOf(body.getOrDefault("stationId", ""));
        String hint = body == null ? "" : String.valueOf(body.getOrDefault("hint", body.getOrDefault("query", "")));
        if ("null".equals(hint)) {
            hint = "ਮਨ ਤੂੰ ਜੋਤਿ";
        }
        result.put("status", "stub");
        result.put("stationId", stationId);
        result.put("listening", true);
        result.put("message", "Live audio attach ready; plug ASR to replace stub transcript.");

        Map<String, Object> transcriptBody = new LinkedHashMap<>();
        transcriptBody.put("hint", hint);
        Map<String, Object> transcript = transcribe(transcriptBody);
        result.put("transcript", transcript);

        Map<String, Object> searchBody = new LinkedHashMap<>();
        searchBody.put("query", transcript.get("transcript"));
        searchBody.put("mode", "word");
        searchBody.put("source", body == null ? "G" : body.getOrDefault("source", "G"));
        result.put("search", aiSearch(searchBody));
        return result;
    }

    @GetMapping("/audio")
    public ResponseEntity<List<Map<String, Object>>> audioCatalog() {
        return ResponseEntity.ok(List.of());
    }

    private static Map<String, Object> sampleAiMatch(String label) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("shabadId", 1);
        row.put("verseId", 1);
        row.put("title", "ਮਨ ਤੂੰ ਜੋਤਿ ਸਰੂਪੁ ਹੈ");
        row.put("snippet", label == null || label.isBlank() ? "Mock match until MySQL is configured" : label);
        row.put("ang", 441);
        row.put("sourceCode", "G");
        row.put("confidence", 0.7);
        row.put("gurmukhi", "mnu qUM joiq srUpu hY");
        row.put("unicode", "ਮਨ ਤੂੰ ਜੋਤਿ ਸਰੂਪੁ ਹੈ");
        return row;
    }
}
