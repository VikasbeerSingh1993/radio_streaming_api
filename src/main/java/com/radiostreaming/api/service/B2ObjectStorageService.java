package com.radiostreaming.api.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Uploads public catalog images to Backblaze B2 via the S3-compatible API.
 * Credentials come from Mongo {@code app_credentials} type {@code B2}.
 */
@Service
public class B2ObjectStorageService {

    private static final Logger log = LoggerFactory.getLogger(B2ObjectStorageService.class);
    private static final Set<String> ALLOWED_CONTENT = Set.of(
            "image/jpeg", "image/jpg", "image/png", "image/webp", "image/gif");
    private static final String DEFAULT_FOLDER = "radio_catalog";

    private final CredentialService credentialService;

    public B2ObjectStorageService(CredentialService credentialService) {
        this.credentialService = credentialService;
    }

    public Map<String, String> uploadCatalogImage(MultipartFile file, String folder) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is required");
        }
        String contentType = normalizeContentType(file.getContentType(), file.getOriginalFilename());
        if (!ALLOWED_CONTENT.contains(contentType)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only JPEG, PNG, WebP, or GIF images are allowed");
        }

        B2Config config = loadConfig();
        String safeFolder = sanitizeFolder(folder);
        String ext = extensionFor(contentType, file.getOriginalFilename());
        String key = config.keyPrefix()
                + safeFolder + "/"
                + Instant.now().toEpochMilli() + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 12)
                + ext;

        try (S3Client client = buildClient(config)) {
            PutObjectRequest req = PutObjectRequest.builder()
                    .bucket(config.bucket())
                    .key(key)
                    .contentType(contentType)
                    .contentLength(file.getSize())
                    .build();
            client.putObject(req, RequestBody.fromBytes(file.getBytes()));
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read uploaded file");
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("B2 upload failed: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Could not upload image to storage");
        }

        String url = publicUrl(config, key);
        log.info("Uploaded catalog image to B2 key={}", key);
        return Map.of(
                "url", url,
                "key", key,
                "bucket", config.bucket(),
                "contentType", contentType);
    }

    private B2Config loadConfig() {
        String bucket = trim(credentialService.decryptedField(CredentialService.TYPE_B2, "bucket"));
        String keyId = firstNonBlank(
                credentialService.decryptedField(CredentialService.TYPE_B2, "applicationKeyId"),
                credentialService.decryptedField(CredentialService.TYPE_B2, "application_key_id"),
                credentialService.decryptedField(CredentialService.TYPE_B2, "keyId"));
        String appKey = firstNonBlank(
                credentialService.decryptedField(CredentialService.TYPE_B2, "applicationKey"),
                credentialService.decryptedField(CredentialService.TYPE_B2, "application_key"),
                credentialService.decryptedField(CredentialService.TYPE_B2, "secretKey"));
        String region = firstNonBlank(
                credentialService.decryptedField(CredentialService.TYPE_B2, "region"),
                "us-east-005");
        String endpoint = firstNonBlank(
                credentialService.decryptedField(CredentialService.TYPE_B2, "endpointUrl"),
                credentialService.decryptedField(CredentialService.TYPE_B2, "endpoint"),
                "https://s3." + region + ".backblazeb2.com");
        String prefix = trim(credentialService.decryptedField(CredentialService.TYPE_B2, "prefix"));
        String publicBase = firstNonBlank(
                credentialService.decryptedField(CredentialService.TYPE_B2, "publicBaseUrl"),
                credentialService.decryptedField(CredentialService.TYPE_B2, "publicUrl"));

        if (bucket.isBlank() || keyId.isBlank() || appKey.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "B2 storage credentials are not configured");
        }

        String keyPrefix = "";
        if (!prefix.isBlank() && !DEFAULT_FOLDER.equalsIgnoreCase(prefix)) {
            // Keep OCR paddle_dataset separate; only use prefix when it is a catalog root.
            if (!"paddle_dataset".equalsIgnoreCase(prefix)) {
                keyPrefix = prefix.replaceAll("^/+|/+$", "") + "/";
            }
        }
        return new B2Config(bucket, keyId, appKey, region, endpoint.replaceAll("/+$", ""), keyPrefix, publicBase);
    }

    private static S3Client buildClient(B2Config config) {
        return S3Client.builder()
                .endpointOverride(URI.create(config.endpoint()))
                .region(Region.of(config.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(config.keyId(), config.appKey())))
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .build();
    }

    private static String publicUrl(B2Config config, String key) {
        if (config.publicBase() != null && !config.publicBase().isBlank()) {
            return config.publicBase().replaceAll("/+$", "") + "/" + key;
        }
        // Path-style friendly URL for B2 S3 endpoint
        return config.endpoint() + "/" + config.bucket() + "/" + key;
    }

    private static String normalizeContentType(String raw, String filename) {
        if (raw != null && !raw.isBlank() && !"application/octet-stream".equalsIgnoreCase(raw)) {
            return raw.trim().toLowerCase(Locale.ROOT);
        }
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".png")) return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        if (name.endsWith(".gif")) return "image/gif";
        return "image/jpeg";
    }

    private static String extensionFor(String contentType, String filename) {
        if (contentType.contains("png")) return ".png";
        if (contentType.contains("webp")) return ".webp";
        if (contentType.contains("gif")) return ".gif";
        if (filename != null) {
            String lower = filename.toLowerCase(Locale.ROOT);
            int dot = lower.lastIndexOf('.');
            if (dot > 0 && lower.length() - dot <= 5) {
                return lower.substring(dot);
            }
        }
        return ".jpg";
    }

    private static String sanitizeFolder(String folder) {
        if (folder == null || folder.isBlank()) {
            return DEFAULT_FOLDER;
        }
        String cleaned = folder.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/_-]", "");
        cleaned = cleaned.replaceAll("^/+|/+$", "");
        return cleaned.isBlank() ? DEFAULT_FOLDER : cleaned;
    }

    private static String trim(String value) {
        return value == null ? "" : value.trim();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return "";
    }

    private record B2Config(
            String bucket,
            String keyId,
            String appKey,
            String region,
            String endpoint,
            String keyPrefix,
            String publicBase) {
    }
}
