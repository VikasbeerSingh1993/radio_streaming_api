package com.radiostreaming.api.config;

import com.radiostreaming.api.credentials.AppCredentialsReader;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Map;

/**
 * Primary MySQL pool for Gurbani {@code bani_search}.
 */
@Configuration
public class MysqlConfig {

    private static final Logger log = LoggerFactory.getLogger(MysqlConfig.class);

    @Bean(name = "baniSearchDataSource", destroyMethod = "close")
    @Primary
    public DataSource baniSearchDataSource(AppCredentialsReader credentialsReader) {
        Map<String, String> merged = credentialsReader.resolveMysql();
        return buildPool("bani-search-mysql", merged, firstNonBlank(merged.get("database"), "bani_search"));
    }

    @Bean
    @Primary
    public JdbcTemplate jdbcTemplate(@Qualifier("baniSearchDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    static HikariDataSource buildPool(String poolName, Map<String, String> creds, String database) {
        String host = creds.get("host");
        int port = parsePort(creds.get("port"), 3306);
        String username = creds.get("username");
        String password = creds.get("password");
        boolean useSsl = Boolean.parseBoolean(creds.getOrDefault("useSsl", "false"));

        HikariConfig config = new HikariConfig();
        config.setPoolName(poolName);
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
        log.info("Created MySQL DataSource '{}' for DB '{}' at {}:{}", poolName, database, host, port);
        pingQuietly(dataSource, host, port, database);
        return dataSource;
    }

    static void ensureDatabaseExists(Map<String, String> creds, String database) {
        String host = creds.get("host");
        int port = parsePort(creds.get("port"), 3306);
        String username = creds.get("username");
        String password = creds.get("password") == null ? "" : creds.get("password");
        boolean useSsl = Boolean.parseBoolean(creds.getOrDefault("useSsl", "false"));
        String url = "jdbc:mysql://" + host + ":" + port
                + "/?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC"
                + "&allowPublicKeyRetrieval=true&useSSL=" + useSsl;
        try (Connection connection = DriverManager.getConnection(url, username, password);
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("CREATE DATABASE IF NOT EXISTS `" + database
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
            log.info("Ensured MySQL database '{}' exists on {}:{}", database, host, port);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not create MySQL database " + database + ": " + ex.getMessage(), ex);
        }
    }

    static void applySchema(DataSource dataSource, String classpathSql) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.addScript(new ClassPathResource(classpathSql));
        populator.setContinueOnError(true);
        populator.execute(dataSource);
        log.info("Applied schema script {}", classpathSql);
    }

    private static void pingQuietly(HikariDataSource dataSource, String host, int port, String database) {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var rs = statement.executeQuery("SELECT 1")) {
            if (rs.next()) {
                log.info("MySQL connectivity OK for {} at {}:{}", database, host, port);
            }
        } catch (Exception ex) {
            log.warn("MySQL not reachable at {}:{} — DB '{}' may be unavailable. {}",
                    host, port, database, ex.getMessage());
        }
    }

    static String firstNonBlank(String... values) {
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

    static int parsePort(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }
}
