package com.radiostreaming.api.service;

import com.mongodb.ConnectionString;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class MongoConnectionFields {

    private MongoConnectionFields() {
    }

    public static Map<String, String> fromUri(String uri, String fallbackDatabase) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("username", "");
        fields.put("password", "");
        fields.put("cluster", "");
        fields.put("database", fallbackDatabase == null ? "" : fallbackDatabase.trim());
        fields.put("srv", "true");
        if (uri == null || uri.isBlank()) {
            return fields;
        }
        ConnectionString connection = new ConnectionString(uri.trim());
        if (connection.getCredential() != null) {
            fields.put("username", nullToEmpty(connection.getCredential().getUserName()));
            char[] password = connection.getCredential().getPassword();
            fields.put("password", password == null ? "" : new String(password));
        }
        List<String> hosts = connection.getHosts();
        if (hosts != null && !hosts.isEmpty()) {
            fields.put("cluster", stripDefaultPort(hosts.getFirst()));
        }
        if (connection.getDatabase() != null && !connection.getDatabase().isBlank()) {
            fields.put("database", connection.getDatabase().trim());
        }
        fields.put("srv", uri.trim().startsWith("mongodb+srv://") ? "true" : "false");
        return fields;
    }

    public static String databaseFromUri(String uri, String fallbackDatabase) {
        Map<String, String> fields = fromUri(uri, fallbackDatabase);
        String database = fields.get("database");
        return database == null || database.isBlank() ? fallbackDatabase : database;
    }

    public static String toUri(Map<String, String> fields) {
        String username = trim(fields == null ? null : fields.get("username"));
        String password = trim(fields == null ? null : fields.get("password"));
        String cluster = trim(fields == null ? null : fields.get("cluster"));
        String database = trim(fields == null ? null : fields.get("database"));
        boolean srv = fields == null || !"false".equalsIgnoreCase(trim(fields.get("srv")));
        String scheme = srv ? "mongodb+srv" : "mongodb";
        return scheme + "://" + encode(username) + ":" + encode(password) + "@" + cluster + "/" + database
                + "?retryWrites=true&w=majority";
    }

    public static boolean isComplete(Map<String, String> fields) {
        return fields != null
                && !trim(fields.get("username")).isBlank()
                && !trim(fields.get("password")).isBlank()
                && !trim(fields.get("cluster")).isBlank()
                && !trim(fields.get("database")).isBlank();
    }

    private static String stripDefaultPort(String host) {
        String value = trim(host);
        if (value.endsWith(":27017")) {
            return value.substring(0, value.length() - ":27017".length());
        }
        return value;
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20");
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
