package com.radiostreaming.api.credentials;

import com.radiostreaming.api.service.MysqlConnectionFields;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Single in-code catalog of bootstrap credential defaults.
 * At startup these are seeded once into Mongo {@code app_credentials} (encrypted).
 * Runtime always reads from that collection via {@link AppCredentialsReader} /
 * {@link com.radiostreaming.api.service.CredentialService} — not from Railway env vars.
 */
public final class CentralCredentialCatalog {

    /** First-time super-admin seed into Mongo {@code admins} only (hashed). Not stored on Railway. */
    public static final String ADMIN_USERNAME = "admin";
    public static final String ADMIN_PASSWORD = "ChangeMeAdmin!23";

    private CentralCredentialCatalog() {
    }

    public static Map<String, String> gmail() {
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

    public static Map<String, String> backblazeB2() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("bucket", "OCRPunjabiData");
        fields.put("prefix", "paddle_dataset");
        fields.put("region", "us-east-005");
        fields.put("endpointUrl", "https://s3.us-east-005.backblazeb2.com");
        fields.put("applicationKeyId", "41a1bdb99cac");
        fields.put("applicationKey", "0055d31ae347164d6f058cd6409e78e80ead403f4f");
        // Optional CDN / friendly public base for catalog images (leave blank to use endpoint/bucket path).
        fields.put("publicBaseUrl", "");
        return fields;
    }

    public static Map<String, String> geo() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("provider", "photon");
        fields.put("apiKey", "");
        return fields;
    }

    /** Gurbani MySQL {@code bani_search} only. */
    public static Map<String, String> mysql() {
        return MysqlConnectionFields.defaults();
    }
}
