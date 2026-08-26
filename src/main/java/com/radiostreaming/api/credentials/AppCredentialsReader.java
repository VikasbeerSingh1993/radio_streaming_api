package com.radiostreaming.api.credentials;

import com.radiostreaming.api.service.CredentialCrypto;
import com.radiostreaming.api.service.MysqlConnectionFields;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.Set;

/**
 * Early/runtime helper to load decrypted credential fields from Mongo {@code app_credentials}.
 * Used by config beans that start before {@link com.radiostreaming.api.service.CredentialService}
 * is fully available, and by bootstrap to keep one read path.
 */
@Component
public class AppCredentialsReader {

    private static final Logger log = LoggerFactory.getLogger(AppCredentialsReader.class);
    private static final Set<String> SECRET_KEYS = Set.of(
            "password", "applicationkey", "application_key", "secret", "secretkey", "appkey",
            "apikey", "api_key");

    private final MongoTemplate mongoTemplate;
    private final CredentialCrypto crypto;

    public AppCredentialsReader(MongoTemplate mongoTemplate, CredentialCrypto crypto) {
        this.mongoTemplate = mongoTemplate;
        this.crypto = crypto;
    }

    public Optional<Map<String, String>> findDecrypted(String type) {
        try {
            Document stored = mongoTemplate.getCollection("app_credentials")
                    .find(new Document("type", Pattern.compile("^" + Pattern.quote(type) + "$", Pattern.CASE_INSENSITIVE)))
                    .first();
            if (stored == null) {
                return Optional.empty();
            }
            Map<String, String> fields = stringMap(stored.get("fields"));
            fields.replaceAll((key, value) -> isSecretKey(key) ? nullToEmpty(crypto.decrypt(value)) : nullToEmpty(value));
            return Optional.of(fields);
        } catch (Exception ex) {
            log.warn("Could not load {} from app_credentials", type, ex);
            return Optional.empty();
        }
    }

    /**
     * MySQL connection fields: prefer Mongo row, else code catalog; never Railway env.
     * Loopback hosts are rewritten to the catalog default so Docker cannot poison the pool.
     */
    public Map<String, String> resolveMysql() {
        Map<String, String> fromTable = findDecrypted("MYSQL").orElseGet(Map::of);
        Map<String, String> merged;
        String source;
        if (MysqlConnectionFields.isComplete(fromTable)) {
            merged = MysqlConnectionFields.mergeWithDefaults(fromTable);
            source = "app_credentials";
        } else {
            merged = CentralCredentialCatalog.mysql();
            source = "central-catalog";
        }
        if (isLoopbackHost(merged.get("host"))) {
            merged.put("host", MysqlConnectionFields.DEFAULT_HOST);
            source = source + "+loopback-fix";
        }
        log.info("Resolved MySQL credentials from {}", source);
        return merged;
    }

    private static Map<String, String> stringMap(Object fields) {
        Map<String, String> map = new LinkedHashMap<>();
        if (fields instanceof Document document) {
            document.forEach((k, v) -> map.put(k, v == null ? "" : v.toString()));
        } else if (fields instanceof Map<?, ?> raw) {
            raw.forEach((k, v) -> map.put(String.valueOf(k), v == null ? "" : v.toString()));
        }
        return map;
    }

    private static boolean isSecretKey(String key) {
        return key != null && SECRET_KEYS.contains(key.replace(" ", "").toLowerCase(Locale.ROOT));
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean isLoopbackHost(String host) {
        if (host == null || host.isBlank()) {
            return false;
        }
        String h = host.trim().toLowerCase(Locale.ROOT);
        return "127.0.0.1".equals(h) || "localhost".equals(h) || "::1".equals(h) || "0.0.0.0".equals(h);
    }
}
