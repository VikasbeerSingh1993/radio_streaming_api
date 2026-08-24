package com.radiostreaming.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

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
            credentialService.seedIfMissing(CredentialService.TYPE_GMAIL, gmailFields());
            credentialService.seedIfMissing(CredentialService.TYPE_B2, b2Fields());
            credentialService.seedIfMissing(CredentialService.TYPE_GEO, geoFields());
            credentialService.seedIfMissing(CredentialService.TYPE_MONGO, mongoFields());
        } catch (Exception ex) {
            log.warn("Could not seed app credentials; they can be added from admin Settings", ex);
        }
    }

    private Map<String, String> mongoFields() {
        return MongoConnectionFields.fromUri(mongoUri, mongoDatabase);
    }

    private static Map<String, String> gmailFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("host", "smtp.gmail.com");
        fields.put("port", "587");
        fields.put("username", "vikasbeersingh@gmail.com");
        fields.put("from", "vikasbeersingh@gmail.com");
        fields.put("password", "yihnevjxpstfmtun");
        fields.put("auth", "true");
        fields.put("starttls", "true");
        return fields;
    }

    private static Map<String, String> b2Fields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("bucket", "OCRPunjabiData");
        fields.put("prefix", "paddle_dataset");
        fields.put("region", "us-east-005");
        fields.put("endpointUrl", "https://s3.us-east-005.backblazeb2.com");
        fields.put("applicationKeyId", "41a1bdb99cac");
        fields.put("applicationKey", "0055d31ae347164d6f058cd6409e78e80ead403f4f");
        return fields;
    }

    private static Map<String, String> geoFields() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("provider", "countrystatecity");
        fields.put("apiKey", "");
        return fields;
    }
}
