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
        putSetting("topbar_right_text", "Waheguru Ji Ka Khalsa · Waheguru Ji Ki Fateh", now);
        putSetting("topbar_visible", "true", now);
        putSetting("topbar_left_visible", "true", now);
        putSetting("topbar_right_visible", "true", now);
        putSetting("footer_text", "Live Kirtan, Gurbani search, and practical tools for sangat and seva.", now);
        putSetting("footer_copyright", "Divine Bliss", now);
        putSetting("nav_home", "Home", now);
        putSetting("nav_services", "Services", now);
        putSetting("nav_radio", "Live Kirtan & Radio", now);
        putSetting("nav_gurbani", "Gurbani Search", now);
        putSetting("nav_gurbani_ai", "Gurbani AI Search", now);
        putSetting("nav_history", "Sikh History", now);
        putSetting("nav_ocr", "Punjabi OCR", now);
        putSetting("nav_ai_images", "Sikhism AI Images", now);
        putSetting("nav_plans", "Plans", now);

        putPage("home",
                "Listen, learn, and serve with clarity",
                "Live Kirtan, Gurbani search, and practical tools — calm, clear, and ready for everyday use.",
                "https://images.unsplash.com/photo-1582510003544-4d00b7f74220?auto=format&fit=crop&w=1600&q=80",
                now);
        putPage("plans",
                "Choose a plan",
                "Start with the AI Free tier at ₹100 per month for Sikh History, Punjabi OCR, Sikhism AI Images, and Gurbani AI Search. Upgrade for higher daily limits.",
                null,
                now);
        putPage("radio",
                "Live Kirtan & Radio",
                "Live broadcasts, radio stations, and Gurbani audio collections — search by name or category.",
                null,
                now);
        putPage("gurbani",
                "Gurbani Search",
                "Find lines by word or starting letters. Open LCD view for single-line projector display.",
                null,
                now);
        putPage("history",
                "Sikh History",
                "Ask about the Gurus, sakhis, and Sikh heritage. Each question uses credits from your plan.",
                null,
                now);
        putPage("gurbani-ai",
                "Gurbani AI Search",
                "Use your voice, or audio from live kirtan, to find matching lines in the Gurbani database. Each search uses plan credits.",
                null,
                now);
        // Refresh copy if an older Q&A subtitle was seeded earlier
        jdbc.update("""
                UPDATE site_pages SET title = ?, subtitle = ?, updated_at = ?
                WHERE page_key = 'gurbani-ai'
                  AND (subtitle LIKE '%natural questions%' OR subtitle LIKE '%Q&A%' OR title = 'Gurbani AI')
                """,
                "Gurbani AI Search",
                "Use your voice, or audio from live kirtan, to find matching lines in the Gurbani database. Each search uses plan credits.",
                Timestamp.from(now));
        jdbc.update("""
                UPDATE site_settings SET setting_value = ?, updated_at = ?
                WHERE setting_key = 'nav_gurbani_ai'
                  AND (setting_value IS NULL OR setting_value IN ('Gurbani AI', 'Gurbai AI', ''))
                """,
                "Gurbani AI Search", Timestamp.from(now));

        putPage("ocr",
                "Punjabi OCR",
                "Upload a photo of Gurmukhi text and read it on screen. Each scan uses plan credits.",
                null,
                now);
        putPage("ai-images",
                "Sikhism AI Images",
                "Describe a respectful scene and generate Sikh-inspired artwork. Each image uses plan credits.",
                null,
                now);
        putPage("services",
                "Services",
                "Listen and search freely. Sign in and choose a plan when you need OCR, AI images, or voice search.",
                null,
                now);

        putMedia("home_hero", "Home hero", "https://images.unsplash.com/photo-1582510003544-4d00b7f74220?auto=format&fit=crop&w=1600&q=80", "/", 1, now);
        putMedia("home_feature_1", "Live Kirtan", "https://images.unsplash.com/photo-1548013146-72479768bada?auto=format&fit=crop&w=800&q=80", "/radio", 2, now);
        putMedia("home_feature_2", "Gurbani", "https://images.unsplash.com/photo-1606983340126-99ab4feaa64a?auto=format&fit=crop&w=800&q=80", "/gurbani", 3, now);
        putMedia("home_feature_3", "Tools for seva", "https://images.unsplash.com/photo-1456513080080-7e9aa9d2c4a5?auto=format&fit=crop&w=800&q=80", "/services", 4, now);

        refreshStaleCopy(now);
    }

    private void refreshStaleCopy(Instant now) {
        jdbc.update("""
                UPDATE site_pages SET title = ?, subtitle = ?, updated_at = ?
                WHERE page_key = 'services'
                  AND (title = 'Our services'
                    OR subtitle LIKE '%AI tools need%'
                    OR subtitle LIKE '%Subscription required%')
                """,
                "Services",
                "Listen and search freely. Sign in and choose a plan when you need OCR, AI images, or voice search.",
                Timestamp.from(now));

        jdbc.update("""
                UPDATE site_pages SET title = ?, subtitle = ?, updated_at = ?
                WHERE page_key = 'history' AND title = 'Sikh History AI'
                """,
                "Sikh History",
                "Ask about the Gurus, sakhis, and Sikh heritage. Each question uses credits from your plan.",
                Timestamp.from(now));

        jdbc.update("""
                UPDATE site_pages SET title = ?, subtitle = ?, updated_at = ?
                WHERE page_key = 'ai-images'
                  AND (title = 'AI Images' OR subtitle LIKE '%Uses plan credits%')
                """,
                "Sikhism AI Images",
                "Describe a respectful scene and generate Sikh-inspired artwork. Each image uses plan credits.",
                Timestamp.from(now));

        jdbc.update("""
                UPDATE site_pages SET title = ?, subtitle = ?, updated_at = ?
                WHERE page_key = 'plans'
                  AND (title LIKE '%subscription%' OR subtitle LIKE '%Start with AI Free Tier%'
                    OR subtitle LIKE '%Credits cover%')
                """,
                "Choose a plan",
                "Start with the AI Free tier at ₹100 per month for Sikh History, Punjabi OCR, Sikhism AI Images, and Gurbani AI Search. Upgrade for higher daily limits.",
                Timestamp.from(now));

        jdbc.update("""
                UPDATE site_pages SET subtitle = ?, updated_at = ?
                WHERE page_key = 'ocr' AND subtitle LIKE '%Uses plan credits%'
                """,
                "Upload a photo of Gurmukhi text and read it on screen. Each scan uses plan credits.",
                Timestamp.from(now));

        jdbc.update("""
                UPDATE site_pages SET subtitle = ?, updated_at = ?
                WHERE page_key = 'gurbani-ai'
                  AND (subtitle LIKE '%Uses plan credits%' OR subtitle LIKE '%Use voice or live kirtan audio%')
                """,
                "Use your voice, or audio from live kirtan, to find matching lines in the Gurbani database. Each search uses plan credits.",
                Timestamp.from(now));

        jdbc.update("""
                UPDATE site_settings SET setting_value = ?, updated_at = ?
                WHERE setting_key = 'footer_text'
                  AND setting_value LIKE '%respectful AI tools%'
                """,
                "Live Kirtan, Gurbani search, and practical tools for sangat and seva.",
                Timestamp.from(now));

        jdbc.update("""
                UPDATE site_settings SET setting_value = ?, updated_at = ?
                WHERE setting_key = 'nav_ai_images' AND setting_value = 'AI Images'
                """,
                "Sikhism AI Images", Timestamp.from(now));
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
