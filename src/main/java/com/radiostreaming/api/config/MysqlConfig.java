package com.radiostreaming.api.config;

import com.radiostreaming.api.service.CredentialCrypto;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Singleton MySQL DataSource for {@code bani_search} (HikariCP one pool).
 * Credentials: {@code app_credentials} type MYSQL, else {@code app.mysql.*} / env bootstrap.
 */
@Configuration
public class MysqlConfig {

    private static final Logger log = LoggerFactory.getLogger(MysqlConfig.class);

    @Bean(destroyMethod = "close")
    public DataSource dataSource(
            MongoTemplate mongoTemplate,
            CredentialCrypto crypto,
            @Value("${app.mysql.host:}") String envHost,
            @Value("${app.mysql.port:3306}") int envPort,
            @Value("${app.mysql.username:}") String envUsername,
            @Value("${app.mysql.password:}") String envPassword,
            @Value("${app.mysql.database:bani_search}") String envDatabase) {
        Map<String, String> fields = loadMysqlFields(mongoTemplate, crypto);
        String host = firstNonBlank(fields.get("host"), envHost, "127.0.0.1");
        int port = parsePort(firstNonBlank(fields.get("port"), String.valueOf(envPort)), envPort);
        String username = firstNonBlank(fields.get("username"), envUsername);
        String password = firstNonBlank(fields.get("password"), envPassword);
        String database = firstNonBlank(fields.get("database"), envDatabase, "bani_search");

        HikariConfig config = new HikariConfig();
        config.setPoolName("bani-search-mysql");
        config.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8&connectionCollation=utf8mb4_0900_ai_ci"
                + "&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false");
        config.setUsername(username);
        config.setPassword(password == null ? "" : password);
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(15_000);
        config.setInitializationFailTimeout(-1);
        config.setDriverClassName("com.mysql.cj.jdbc.Driver");

        HikariDataSource dataSource = new HikariDataSource(config);
        log.info("Created singleton MySQL DataSource for database '{}' at {}:{}", database, host, port);
        return dataSource;
    }

    @Bean
    public JdbcTemplate jdbcTemplate(DataSource dataSource) {
        return new JdbcTemplate(dataSource);
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
            log.warn("Could not load MYSQL credentials from app_credentials; using env bootstrap", ex);
        }
        return fields;
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
