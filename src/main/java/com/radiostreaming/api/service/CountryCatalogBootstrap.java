package com.radiostreaming.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
public class CountryCatalogBootstrap {

    private static final Logger log = LoggerFactory.getLogger(CountryCatalogBootstrap.class);
    private final GeoService geoService;

    public CountryCatalogBootstrap(GeoService geoService) {
        this.geoService = geoService;
    }

    @Order(1)
    @EventListener(ApplicationReadyEvent.class)
    public void seedCountries() {
        try {
            int count = geoService.countries().size();
            log.info("Country catalog ready with {} countries", count);
        } catch (Exception ex) {
            log.warn("Could not seed country catalog; it will load on first request", ex);
        }
    }
}
