package com.radiostreaming.api.config;

import com.mongodb.ConnectionString;
import com.mongodb.MongoClientSettings;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.model.Filters;
import com.radiostreaming.api.service.CredentialCrypto;
import com.radiostreaming.api.service.MongoConnectionFields;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Provides a single shared {@link MongoClient}. The Railway {@code MONGODB_URI} is only a bootstrap
 * so the process can read {@code app_credentials}. Username, password, cluster, and database are
 * then loaded from that common table and used for the live client.
 */
@Configuration
public class MongoConfig {

    private static final Logger log = LoggerFactory.getLogger(MongoConfig.class);

    @Bean(destroyMethod = "close")
    public MongoClient mongoClient(
            @Value("${spring.data.mongodb.uri}") String bootstrapUri,
            @Value("${spring.data.mongodb.database:divine_bliss_streaming}") String defaultDatabase,
            CredentialCrypto crypto) {
        log.info("Creating singleton MongoClient");
        MongoClient bootstrap = create(bootstrapUri);
        MongoClient fromTable = null;
        try {
            String dbName = MongoConnectionFields.databaseFromUri(bootstrapUri, defaultDatabase);
            Document stored = bootstrap.getDatabase(dbName)
                    .getCollection("app_credentials")
                    .find(Filters.regex("type", "^MONGO$", "i"))
                    .first();
            if (stored == null) {
                log.info("Mongo credentials are not in app_credentials yet; using bootstrap URI until they are seeded");
                return bootstrap;
            }
            Map<String, String> fields = stringMap(stored.get("fields"));
            String decrypted = crypto.decrypt(fields.get("password"));
            fields.put("password", decrypted == null ? "" : decrypted);
            if (!MongoConnectionFields.isComplete(fields)) {
                log.warn("Mongo row in app_credentials is incomplete; using bootstrap URI");
                return bootstrap;
            }
            String uri = MongoConnectionFields.toUri(fields);
            fromTable = create(uri);
            fromTable.getDatabase(fields.get("database")).runCommand(new Document("ping", 1));
            bootstrap.close();
            log.info("Using Mongo username/cluster/database from app_credentials");
            return fromTable;
        } catch (Exception ex) {
            if (fromTable != null) {
                try {
                    fromTable.close();
                } catch (Exception ignored) {
                    // bootstrap client is still used
                }
            }
            log.warn("Could not load Mongo credentials from app_credentials; using bootstrap URI", ex);
            return bootstrap;
        }
    }

    @Bean
    public MongoCustomConversions mongoCustomConversions() {
        return new MongoCustomConversions(List.of(new ObjectIdToStringConverter()));
    }

    private static MongoClient create(String uri) {
        MongoClientSettings settings = MongoClientSettings.builder()
                .applyConnectionString(new ConnectionString(uri))
                .applyToConnectionPoolSettings(pool -> pool
                        .minSize(1)
                        .maxSize(20)
                        .maxWaitTime(30, TimeUnit.SECONDS))
                .build();
        return MongoClients.create(settings);
    }

    @SuppressWarnings("unchecked")
    private static Map<String, String> stringMap(Object fields) {
        Map<String, String> map = new LinkedHashMap<>();
        if (fields instanceof Document document) {
            for (Map.Entry<String, Object> entry : document.entrySet()) {
                map.put(entry.getKey(), entry.getValue() == null ? "" : entry.getValue().toString());
            }
        } else if (fields instanceof Map<?, ?> raw) {
            raw.forEach((key, value) -> map.put(String.valueOf(key), value == null ? "" : value.toString()));
        }
        return map;
    }

    static class ObjectIdToStringConverter implements Converter<ObjectId, String> {
        @Override
        public String convert(ObjectId source) {
            return source.toHexString();
        }
    }
}
