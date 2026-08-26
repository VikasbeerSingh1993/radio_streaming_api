package com.radiostreaming.api.config;

import com.radiostreaming.api.credentials.AppCredentialsReader;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Connection;
import java.util.Map;

/**
 * Singleton HikariCP DataSource for Gurbani MySQL {@code bani_search} only.
 * Credentials come only from the central Mongo {@code app_credentials} store
 * (seeded from {@link com.radiostreaming.api.credentials.CentralCredentialCatalog}).
 * No Railway / env MySQL secrets.
 */
@Configuration
public class MysqlConfig {

    private static final Logger log = LoggerFactory.getLogger(MysqlConfig.class);

    @Bean(destroyMethod = "close")
    public DataSource dataSource(AppCredentialsReader credentialsReader) {
        Map<String, String> merged = credentialsReader.resolveMysql();
        String host = merged.get("host");
        int port = parsePort(merged.get("port"), 3306);
        String username = merged.get("username");
        String password = merged.get("password");
        String database = firstNonBlank(merged.get("database"), "bani_search");
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
        log.info("Created singleton MySQL DataSource for Gurbani DB '{}' at {}:{}", database, host, port);
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
