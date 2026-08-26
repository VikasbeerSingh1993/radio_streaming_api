package com.radiostreaming.api.service;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Bootstrap defaults for {@code app_credentials} type MYSQL ({@code bani_search} only).
 * Stations / catalog stay on Mongo. Password is encrypted at rest via CredentialService.
 */
public final class MysqlConnectionFields {

    public static final String DEFAULT_HOST = "129.225.124.207";
    public static final String DEFAULT_PORT = "3306";
    public static final String DEFAULT_USERNAME = "vikasbeer1313";
    public static final String DEFAULT_PASSWORD = "Vaheguru@964625";
    public static final String DEFAULT_DATABASE = "bani_search";
    public static final String DEFAULT_USE_SSL = "false";

    private MysqlConnectionFields() {
    }

    public static Map<String, String> defaults() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("host", DEFAULT_HOST);
        fields.put("port", DEFAULT_PORT);
        fields.put("username", DEFAULT_USERNAME);
        fields.put("password", DEFAULT_PASSWORD);
        fields.put("database", DEFAULT_DATABASE);
        fields.put("useSsl", DEFAULT_USE_SSL);
        return fields;
    }

    public static boolean isComplete(Map<String, String> fields) {
        if (fields == null || fields.isEmpty()) {
            return false;
        }
        return notBlank(fields.get("host"))
                && notBlank(fields.get("username"))
                && notBlank(fields.get("password"))
                && notBlank(firstNonBlank(fields.get("database"), DEFAULT_DATABASE));
    }

    public static Map<String, String> mergeWithDefaults(Map<String, String> stored) {
        Map<String, String> merged = defaults();
        if (stored != null) {
            stored.forEach((key, value) -> {
                if (value != null && !value.isBlank()) {
                    merged.put(key, value.trim());
                }
            });
        }
        if (!notBlank(merged.get("database"))) {
            merged.put("database", DEFAULT_DATABASE);
        }
        if (!notBlank(merged.get("port"))) {
            merged.put("port", DEFAULT_PORT);
        }
        if (!notBlank(merged.get("useSsl"))) {
            merged.put("useSsl", DEFAULT_USE_SSL);
        }
        return merged;
    }

    private static boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value.trim();
            }
        }
        return "";
    }
}
