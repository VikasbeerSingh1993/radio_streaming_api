package com.radiostreaming.api.config;

import com.radiostreaming.api.service.CredentialCrypto;
import com.radiostreaming.api.service.MysqlConnectionFields;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Singleton HikariCP DataSource for Gurbani MySQL {@code bani_search} only.
 * Stations / events / audio catalog stay on MongoDB.
 * Prefers encrypted {@code app_credentials} type MYSQL; bootstrap defaults fill gaps
 * so the first boot can connect before ApplicationReady seeds the table.
 */
@Configuration
public class MysqlConfig {

    private static final Logger log = LoggerFactory.getLogger(MysqlConfig.class);

    @Bean(destroyMethod = "close")
    public DataSource dataSource(
            MongoTemplate mongoTemplate,
            CredentialCrypto crypto,
            @Value("${app.mysql.host:}") String envHost,
            @Value("${app.mysql.port:}") String envPort,
            @Value("${app.mysql.username:}") String envUsername,
            @Value("${app.mysql.password:}") String envPassword,
            @Value("${app.mysql.database:}") String envDatabase,
            @Value("${app.mysql.use-ssl:}") String envUseSsl) {
        Map<String, String> fromTable = loadMysqlFields(mongoTemplate, crypto);
        Map<String, String> fromEnv = new LinkedHashMap<>();
        putIfPresent(fromEnv, "host", envHost);
        putIfPresent(fromEnv, "port", envPort);
        putIfPresent(fromEnv, "username", envUsername);
        putIfPresent(fromEnv, "password", envPassword);
        putIfPresent(fromEnv, "database", envDatabase);
        putIfPresent(fromEnv, "useSsl", envUseSsl);

        Map<String, String> merged;
        String source;
        if (MysqlConnectionFields.isComplete(fromTable)) {
            merged = MysqlConnectionFields.mergeWithDefaults(fromTable);
            source = "app_credentials";
        } else if (!fromEnv.isEmpty()) {
            merged = MysqlConnectionFields.mergeWithDefaults(fromEnv);
            source = "env+defaults";
        } else {
            merged = MysqlConnectionFields.defaults();
            source = "bootstrap-defaults";
        }

        String host = merged.get("host");
        int port = parsePort(merged.get("port"), 3306);
        String username = merged.get("username");
        String password = merged.get("password");
        String database = firstNonBlank(merged.get("database"), MysqlConnectionFields.DEFAULT_DATABASE);
        boolean useSsl = Boolean.parseBoolean(merged.getOrDefault("useSsl", "false"));

        HikariConfig config = new HikariConfig();
        config.setPoolName("bani-search-mysql");
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&connectionCollation=utf8mb4_0900_ai_ci"
                + "&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=" + useSsl);
        config.setUsername(username);
        config.setPassword(password == null ? "" : password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(12_000);
        config.setInitializationFailTimeout(-1);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        HikariDataSource dataSource = new HikariDataSource(config);
        log.info("Created singleton MySQL DataSource for Gurbani DB '{}' at {}:{} (source={})",
                database, host, port, source);
        pingQuietly(dataSource, host, port, database);
        return dataSource;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    private static void pingQuietly(HikariDataSource dataSource, String host, int port, String database) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var rs = statement.executeQuery("SELECT 1")) {
            if (rs.next()) {
                log.info("MySQL connectivity OK for {} at {}:{}", database, host, port);
            }
        } catch (Exception ex) {
            log.warn("MySQL not reachable at {}:{} — Gurbani search will degrade. {}",
                    host, port, ex.getMessage());
        }
    }

    private static Map<String, String> loadMysqlFields(MongoTemplate mongoTemplate, CredentialCrypto crypto) {
        Map<String, String> fields = new LinkedHashMap<>();
        try {
            Document stored = mongoTemplate.getCollection("app_credentials")
                    .find(new Document("type", Pattern.compile("^MYSQL$", Pattern.CASE_INSENSITIVE)))
                    .first();
            if (stored == null) {
                return fields;
            }
            Object raw = stored.get("fields");
            if (raw instanceof Document document) {
                document.forEach((k, v) -> fields.put(k, v == null ? "" : v.toString()));
            } else if (raw instanceof Map<?, ?> map) {
                map.forEach((k, v) -> fields.put(String.valueOf(k), v == null ? "" : v.toString()));
            }
            if (fields.containsKey("password")) {
                fields.put("password", crypto.decrypt(fields.get("password")));
            }
        } catch (Exception ex) {
            log.warn("Could not load MYSQL credentials from app_credentials; using bootstrap defaults", ex);
        }
        return fields;
    }

    private static void putIfPresent(Map<String, String> target, String key, String value) {
        if (value != null && !value.isBlank()) {
            target.put(key, value.trim());
        }
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

    private static int parsePort(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
