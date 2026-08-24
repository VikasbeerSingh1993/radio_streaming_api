package com.radiostreaming.api.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Provides a single shared {@link MongoClient} for the whole application.
 * Spring Data MongoDB reuses this bean instead of opening extra connections.
 */
@Configuration
public class MongoConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoConfig.class);

    @Bean(destroyMethod = "close")
    public MongoClient mongoClient(@Value("${spring.data.mongodb.uri}") String uri) {
        log.info("Creating singleton MongoClient");
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri))
                .applyToConnectionPoolSettings(pool -> pool
                        .minSize(1)
                        .maxSize(20)
                        .maxWaitTime(30, TimeUnit.SECONDS))
                .build();
        return MongoClients.create(settings);
    }

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(new ObjectIdToStringConverter()));
    }

    static class ObjectIdToStringConverter implements Converter<ObjectId, String> {
        @Override
        public String convert(ObjectId source) {
            return source.toHexString();
        }
    }
}
