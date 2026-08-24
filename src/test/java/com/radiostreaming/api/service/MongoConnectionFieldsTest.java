package com.radiostreaming.api.service;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MongoConnectionFieldsTest {

    @Test
    void parsesAtlasUriIntoSettingsFields() {
        Map<String, String> fields = MongoConnectionFields.fromUri(
                "mongodb+srv://atlasUser:p%40ss@cluster0.abc123.mongodb.net/divine_bliss_streaming?retryWrites=true",
                "fallback_db");

        assertEquals("atlasUser", fields.get("username"));
        assertEquals("p@ss", fields.get("password"));
        assertEquals("cluster0.abc123.mongodb.net", fields.get("cluster"));
        assertEquals("divine_bliss_streaming", fields.get("database"));
        assertEquals("true", fields.get("srv"));
        assertTrue(MongoConnectionFields.isComplete(fields));
    }

    @Test
    void rebuildsUriFromStoredFields() {
        String uri = MongoConnectionFields.toUri(Map.of(
                "username", "atlasUser",
                "password", "p@ss",
                "cluster", "cluster0.abc123.mongodb.net",
                "database", "divine_bliss_streaming",
                "srv", "true"
        ));

        assertTrue(uri.startsWith("mongodb+srv://atlasUser:"));
        assertTrue(uri.contains("@cluster0.abc123.mongodb.net/divine_bliss_streaming"));
        Map<String, String> roundTrip = MongoConnectionFields.fromUri(uri, "fallback_db");
        assertEquals("atlasUser", roundTrip.get("username"));
        assertEquals("p@ss", roundTrip.get("password"));
        assertEquals("divine_bliss_streaming", roundTrip.get("database"));
    }
}
