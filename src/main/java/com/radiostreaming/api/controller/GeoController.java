package com.radiostreaming.api.controller;

import com.radiostreaming.api.dto.GeoPlace;
import com.radiostreaming.api.service.GeoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/geo")
public class GeoController {

    private final GeoService geoService;

    public GeoController(GeoService geoService) {
        this.geoService = geoService;
    }

    @GetMapping("/countries")
    public List<Map<String, String>> countries() {
        return geoService.countries();
    }

    @GetMapping("/states")
    public List<Map<String, String>> states(@RequestParam String countryCode) {
        return geoService.states(countryCode);
    }

    @GetMapping("/cities")
    public List<GeoPlace> cities(
            @RequestParam String countryCode,
            @RequestParam String state,
            @RequestParam(required = false) String q) {
        return geoService.cities(countryCode, state, q);
    }

    @GetMapping("/suggest")
    public List<GeoPlace> suggest(
            @RequestParam String countryCode,
            @RequestParam(required = false) String city,
            @RequestParam String q) {
        return geoService.suggest(countryCode, city, q);
    }
}
