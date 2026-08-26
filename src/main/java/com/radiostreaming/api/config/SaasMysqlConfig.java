package com.radiostreaming.api.config;

import com.radiostreaming.api.credentials.AppCredentialsReader;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.Map;

/**
 * Separate MySQL database {@code divine_bliss_web} for React SaaS + CMS
 * (plans, users, credits, site content). Gurbani stays on {@code bani_search}.
 */
@Configuration
public class SaasMysqlConfig {

    public static final String SAAS_DATABASE = "divine_bliss_web";

    @Bean(name = "saasWebDataSource", destroyMethod = "close")
    public DataSource saasWebDataSource(AppCredentialsReader credentialsReader) {
        Map<String, String> merged = credentialsReader.resolveMysql();
        MysqlConfig.ensureDatabaseExists(merged, SAAS_DATABASE);
        HikariDataSource ds = MysqlConfig.buildPool("divine-bliss-web-mysql", merged, SAAS_DATABASE);
        MysqlConfig.applySchema(ds, "db/saas-schema.sql");
        return ds;
    }

    @Bean(name = "saasJdbcTemplate")
    public JdbcTemplate saasJdbcTemplate(@Qualifier("saasWebDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
