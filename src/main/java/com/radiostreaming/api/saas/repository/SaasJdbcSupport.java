package com.radiostreaming.api.saas.repository;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class SaasJdbcSupport {

    private SaasJdbcSupport() {
    }

    static String newIdIfBlank(String id) {
        return (id == null || id.isBlank()) ? UUID.randomUUID().toString() : id;
    }

    static Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    static boolean toBoolean(int tinyInt) {
        return tinyInt != 0;
    }

    static int toTinyInt(boolean value) {
        return value ? 1 : 0;
    }

    static String writeJson(ObjectMapper mapper, Object value) {
        if (value == null) {
            return null;
        }
        try {
            return mapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize JSON", e);
        }
    }

    static List<String> readStringList(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) {
            return Collections.emptyList();
        }
        try {
            return mapper.readValue(json, new TypeReference<List<String>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse features JSON", e);
        }
    }

    static Map<String, Object> readObjectMap(ObjectMapper mapper, String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return mapper.readValue(json, new TypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse metadata JSON", e);
        }
    }
}
