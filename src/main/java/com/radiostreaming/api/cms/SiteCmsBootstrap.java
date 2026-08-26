package com.radiostreaming.api.cms;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class SiteCmsBootstrap {

    private final JdbcTemplate jdbc;

    public SiteCmsBootstrap(@Qualifier("saasJdbcTemplate") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    @Order(25)
    public void seedDefaults() {
        Instant now = Instant.now();
        putSetting("brand_name", "Divine Bliss", now);
        putSetting("logo_url", "https://images.unsplash.com/photo-1582510003544-4d00b7f74220?auto=format&fit=crop&w=200&q=80", now);
        putSetting("header_tagline", "Kirtan · Gurbani · Seva tools", now);
        putSetting("footer_text", "Live Kirtan, Gurbani search, and respectful AI tools for sangat and seva.", now);
        putSetting("footer_copyright", "Divine Bliss", now);

        putPage("home",
                "Listen, learn, and serve with clarity",
                "Live Kirtan, Gurbani search, and practical tools — calm, clear, and ready for everyday use.",
                "https://images.unsplash.com/photo-1582510003544-4d00b7f74220?auto=format&fit=crop&w=1600&q=80",
                now);
        putPage("plans",
                "Choose a plan that fits your seva",
                "Credits cover Punjabi OCR, AI images, Sikh History AI, and Gurbani AI search.",
                null,
                now);
        putPage("radio",
                "Live Kirtan & Radio",
                "Live broadcasts, radio stations, and Gurbani audio collections — search by name or category.",
                null,
                now);
        putPage("gurbani",
                "Gurbani Search",
                "Find lines by word or starting letters. See Ang, writer, and translations when available.",
                null,
                now);
        putPage("history",
                "Sikh History AI",
                "Ask clear questions about the Gurus, sakhis, and Sikh heritage. Uses plan credits.",
                null,
                now);
        putPage("gurbani-ai",
                "Gurbani AI Search",
                "Ask natural questions about Gurbani meaning and context. Uses plan credits.",
                null,
                now);
        putPage("ocr",
                "Punjabi OCR",
                "Upload a photo of Gurmukhi text and read it on screen. Uses plan credits.",
                null,
                now);
        putPage("ai-images",
                "AI Images",
                "Create respectful Sikh-inspired artwork from a short description. Uses plan credits.",
                null,
                now);
        putPage("services",
                "Our services",
                "Open listening and scripture tools for everyone. Paid AI tools need an account and plan.",
                null,
                now);

        putMedia("home_hero", "Home hero", "https://images.unsplash.com/photo-1582510003544-4d00b7f74220?auto=format&fit=crop&w=1600&q=80", "/", 1, now);
        putMedia("home_feature_1", "Live Kirtan", "https://images.unsplash.com/photo-1548013146-72479768bada?auto=format&fit=crop&w=800&q=80", "/radio", 2, now);
        putMedia("home_feature_2", "Gurbani", "https://images.unsplash.com/photo-1606983340126-99ab4feaa64a?auto=format&fit=crop&w=800&q=80", "/gurbani", 3, now);
        putMedia("home_feature_3", "Seva tools", "https://images.unsplash.com/photo-1456513080080-7e9aa9d2c4a5?auto=format&fit=crop&w=800&q=80", "/services", 4, now);
    }

    private void putSetting(String key, String value, Instant now) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM site_settings WHERE setting_key = ?", Integer.class, key);
        if (count != null && count > 0) {
            return;
        }
        jdbc.update("INSERT INTO site_settings(setting_key, setting_value, updated_at) VALUES (?,?,?)",
                key, value, Timestamp.from(now));
    }

    private void putPage(String key, String title, String subtitle, String hero, Instant now) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM site_pages WHERE page_key = ?", Integer.class, key);
        if (count != null && count > 0) {
            return;
        }
        jdbc.update("""
                INSERT INTO site_pages(page_key, title, subtitle, body_html, hero_image_url, meta_json, updated_at)
                VALUES (?,?,?,?,?,NULL,?)
                """,
                key, title, subtitle, null, hero, Timestamp.from(now));
    }

    private void putMedia(String slot, String label, String url, String link, int sort, Instant now) {
        Integer count = jdbc.queryForObject("SELECT COUNT(*) FROM site_media WHERE slot_key = ?", Integer.class, slot);
        if (count != null && count > 0) {
            return;
        }
        jdbc.update("""
                INSERT INTO site_media(id, slot_key, label, image_url, link_url, sort_order, active, updated_at)
                VALUES (?,?,?,?,?,?,1,?)
                """,
                java.util.UUID.randomUUID().toString(), slot, label, url, link, sort, Timestamp.from(now));
    }
}
