package com.radiostreaming.api.service;

import com.radiostreaming.api.model.StationDocument;
import com.radiostreaming.api.repository.StationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Keeps known catalog stations readable on the Live Kirtan page — distinct artwork per station
 * and {@code type=live} for live broadcasts that were saved as generic radio rows.
 */
@Component
public class StationCatalogBootstrap {

    private static final Logger log = LoggerFactory.getLogger(StationCatalogBootstrap.class);

    /** Shared placeholder YouTube thumb used for every station during early seeding. */
    private static final String PLACEHOLDER_YT_ID = "qwaqL8k1G9s";

    private static final String HARMANDIR_YT_THUMB =
            "https://i.ytimg.com/vi/" + PLACEHOLDER_YT_ID + "/hqdefault.jpg";

    private static final Map<String, String> AUDIO_STATION_THUMBS = Map.of(
            "6a13322288e4ceea8227cb91",
            "https://images.unsplash.com/photo-1606983340126-99ab4feaa64a?auto=format&fit=crop&w=800&q=80",
            "6a13322288e4ceea8227cb89",
            "https://images.unsplash.com/photo-1456513080080-7e9aa9d2c4a5?auto=format&fit=crop&w=800&q=80"
    );

    private final StationRepository stationRepository;

    public StationCatalogBootstrap(StationRepository stationRepository) {
        this.stationRepository = stationRepository;
    }

    @Order(2)
    @EventListener(ApplicationReadyEvent.class)
    public void refreshKnownStations() {
        try {
            int updated = 0;
            for (StationDocument station : stationRepository.findAll()) {
                if (patchStation(station)) {
                    stationRepository.save(station);
                    updated++;
                }
            }
            if (updated > 0) {
                log.info("Refreshed {} station catalog row(s) for Live Kirtan display", updated);
            }
        } catch (Exception ex) {
            log.warn("Could not refresh station catalog thumbnails", ex);
        }
    }

    private boolean patchStation(StationDocument station) {
        boolean changed = false;
        String id = station.getId();
        String thumb = station.getThumbnail() == null ? "" : station.getThumbnail();

        if (Boolean.TRUE.equals(station.getLive()) || "live_kirtan".equals(station.getCategory())) {
            if (!"live".equalsIgnoreCase(station.getType())) {
                station.setType("live");
                changed = true;
            }
            if (thumb.isBlank() || isPlaceholderYoutube(thumb)) {
                station.setThumbnail(HARMANDIR_YT_THUMB);
                changed = true;
            }
            return changed;
        }

        if ("audio".equalsIgnoreCase(station.getType()) && isPlaceholderYoutube(thumb)) {
            String replacement = AUDIO_STATION_THUMBS.get(id);
            if (replacement == null) {
                replacement = "https://images.unsplash.com/photo-1606983340126-99ab4feaa64a?auto=format&fit=crop&w=800&q=80";
            }
            station.setThumbnail(replacement);
            changed = true;
        }

        return changed;
    }

    private static boolean isPlaceholderYoutube(String thumb) {
        return thumb.contains("/vi/" + PLACEHOLDER_YT_ID + "/") || thumb.contains(PLACEHOLDER_YT_ID);
    }
}
