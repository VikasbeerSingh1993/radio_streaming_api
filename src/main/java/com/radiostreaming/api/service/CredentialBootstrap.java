package com.radiostreaming.api.service;

import com.radiostreaming.api.credentials.CentralCredentialCatalog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Seeds Mongo {@code app_credentials} once from {@link CentralCredentialCatalog}.
 * Does not read secrets from Railway env vars.
 */
@Component
public class CredentialBootstrap {

    private static final Logger log = LoggerFactory.getLogger(CredentialBootstrap.class);

    private final CredentialService credentialService;
    private final String mongoUri;
    private final String mongoDatabase;

    public CredentialBootstrap(
            CredentialService credentialService,
            @Value("${spring.data.mongodb.uri}") String mongoUri,
            @Value("${spring.data.mongodb.database:divine_bliss_streaming}") String mongoDatabase) {
        this.credentialService = credentialService;
        this.mongoUri = mongoUri;
        this.mongoDatabase = mongoDatabase;
    }

    @Order(0)
    @EventListener(ApplicationReadyEvent.class)
    public void seedSharedCredentials() {
        try {
            credentialService.seedIfMissing(CredentialService.TYPE_GMAIL, CentralCredentialCatalog.gmail());
            credentialService.seedOrFillIncomplete(CredentialService.TYPE_GMAIL, CentralCredentialCatalog.gmail());
            credentialService.seedIfMissing(CredentialService.TYPE_B2, CentralCredentialCatalog.backblazeB2());
            credentialService.seedIfMissing(CredentialService.TYPE_GEO, CentralCredentialCatalog.geo());
            credentialService.ensurePhotonProvider();
            // MONGO row is derived from the bootstrap URI so the live client can reload from the table.
            credentialService.seedIfMissing(CredentialService.TYPE_MONGO, MongoConnectionFields.fromUri(mongoUri, mongoDatabase));
            credentialService.seedOrFillIncomplete(CredentialService.TYPE_MYSQL, CentralCredentialCatalog.mysql());
        } catch (Exception ex) {
            log.warn("Could not seed app credentials; they can be added from admin Settings", ex);
        }
    }
}
