package com.radiostreaming.api.service;

import com.radiostreaming.api.model.CredentialDocument;
import com.radiostreaming.api.repository.CredentialRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CredentialService {

    public static final String TYPE_GMAIL = "GMAIL";
    public static final String TYPE_B2 = "B2";
    public static final String TYPE_GEO = "GEO";
    public static final String TYPE_MONGO = "MONGO";
    static final String MASK = "********";
    private static final Logger log = LoggerFactory.getLogger(CredentialService.class);
    private static final Set<String> SECRET_KEYS = Set.of(
            "password", "applicationkey", "application_key", "secret", "secretkey", "appkey",
            "apikey", "api_key");

    private final CredentialRepository repository;
    private final CredentialCrypto crypto;
    private final ConcurrentHashMap<String, CredentialDocument> cache = new ConcurrentHashMap<>();

    public CredentialService(CredentialRepository repository, CredentialCrypto crypto) {
        this.repository = repository;
        this.crypto = crypto;
    }

    public List<Map<String, Object>> listMasked() {
        List<Map<String, Object>> views = new ArrayList<>();
        for (CredentialDocument document : repository.findAll()) {
            cache.put(normalize(document.getType()), document);
            views.add(maskedView(document));
        }
        views.sort(Comparator.comparingInt(view -> switch (String.valueOf(view.get("type"))) {
            case TYPE_GMAIL -> 0;
            case TYPE_B2 -> 1;
            case TYPE_GEO -> 2;
            case TYPE_MONGO -> 3;
            default -> 9;
        }));
        return views;
    }

    public Map<String, Object> getMasked(String type) {
        return maskedView(require(type));
    }

    public Map<String, Object> saveMasked(String type, Map<String, String> incoming) {
        CredentialDocument existing = repository.findByTypeIgnoreCase(type).orElse(null);
        Map<String, String> merged = new LinkedHashMap<>();
        if (existing != null && existing.getFields() != null) {
            merged.putAll(existing.getFields());
        }
        if (incoming != null) {
            incoming.forEach((key, value) -> {
                if (key == null || key.isBlank()) {
                    return;
                }
                if (isSecretKey(key) && isBlankOrMasked(value)) {
                    if (existing == null) {
                        merged.putIfAbsent(key, "");
                    }
                    return;
                }
                if (value == null) {
                    merged.remove(key);
                    return;
                }
                merged.put(key, isSecretKey(key) ? crypto.encrypt(value.trim()) : value.trim());
            });
        }
        encryptSecrets(merged);
        CredentialDocument document = existing == null ? new CredentialDocument() : existing;
        document.setType(normalize(type));
        document.setFields(merged);
        document.setUpdatedAt(Instant.now());
        CredentialDocument saved = repository.save(document);
        cache.put(saved.getType(), saved);
        log.info("Stored {} credentials in MongoDB and refreshed credential cache", saved.getType());
        return maskedView(saved);
    }

    public Optional<CredentialDocument> find(String type) {
        String key = normalize(type);
        CredentialDocument cached = cache.get(key);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<CredentialDocument> loaded = repository.findByTypeIgnoreCase(type);
        loaded.ifPresent(document -> cache.put(key, document));
        return loaded;
    }

    public String decryptedField(String type, String field) {
        CredentialDocument document = require(type);
        String value = document.getFields() == null ? null : document.getFields().get(field);
        return crypto.decrypt(value);
    }

    public String geoApiKey() {
        CredentialDocument document = find(TYPE_GEO).orElse(null);
        if (document == null || document.getFields() == null) {
            return "";
        }
        String value = document.getFields().get("apiKey");
        if (value == null || value.isBlank()) {
            value = document.getFields().get("api_key");
        }
        String decrypted = crypto.decrypt(value);
        return decrypted == null ? "" : decrypted.trim();
    }

    public Optional<JavaMailSender> mailSender() {
        return find(TYPE_GMAIL).map(this::toMailSender);
    }

    public String mailFrom() {
        CredentialDocument gmail = find(TYPE_GMAIL).orElse(null);
        if (gmail == null || gmail.getFields() == null) {
            return "";
        }
        String from = gmail.getFields().get("from");
        if (from == null || from.isBlank()) {
            from = gmail.getFields().get("username");
        }
        return from == null ? "" : from;
    }

    public void seedIfMissing(String type, Map<String, String> fields) {
        if (repository.findByTypeIgnoreCase(type).isPresent()) {
            return;
        }
        saveMasked(type, fields);
        log.info("Seeded {} credentials into MongoDB", normalize(type));
    }

    private CredentialDocument require(String type) {
        return find(type).orElseThrow(() -> new ResponseStatusException(
                HttpStatus.NOT_FOUND, "No credentials stored for type " + type));
    }

    private JavaMailSender toMailSender(CredentialDocument document) {
        Map<String, String> fields = document.getFields() == null ? Map.of() : document.getFields();
        String host = fields.getOrDefault("host", "smtp.gmail.com");
        int port = parsePort(fields.get("port"));
        String username = fields.getOrDefault("username", "");
        String password = crypto.decrypt(fields.get("password"));
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(port);
        if (!username.isBlank()) {
            sender.setUsername(username);
            sender.setPassword(password == null ? "" : password);
        }
        Properties props = sender.getJavaMailProperties();
        props.put("mail.transport.protocol", "smtp");
        props.put("mail.smtp.auth", fields.getOrDefault("auth", "true"));
        props.put("mail.smtp.starttls.enable", fields.getOrDefault("starttls", "true"));
        props.put("mail.smtp.starttls.required", "true");
        return sender;
    }

    private Map<String, Object> maskedView(CredentialDocument document) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", document.getId());
        view.put("type", document.getType());
        view.put("updatedAt", document.getUpdatedAt());
        Map<String, String> fields = new LinkedHashMap<>();
        if (document.getFields() != null) {
            document.getFields().forEach((key, value) ->
                    fields.put(key, isSecretKey(key) && value != null && !value.isBlank() ? MASK : value));
        }
        if (TYPE_GEO.equals(document.getType())) {
            fields.putIfAbsent("provider", "countrystatecity");
            fields.putIfAbsent("apiKey", "");
        }
        if (TYPE_MONGO.equals(document.getType())) {
            fields.putIfAbsent("username", "");
            fields.putIfAbsent("password", "");
            fields.putIfAbsent("cluster", "");
            fields.putIfAbsent("database", "");
        }
        view.put("fields", fields);
        return view;
    }

    private void encryptSecrets(Map<String, String> fields) {
        fields.replaceAll((key, value) -> isSecretKey(key) ? crypto.encrypt(value) : value);
    }

    static boolean isSecretKey(String key) {
        return key != null && SECRET_KEYS.contains(key.replace(" ", "").toLowerCase(Locale.ROOT));
    }

    private static boolean isBlankOrMasked(String value) {
        return value == null || value.isBlank() || MASK.equals(value);
    }

    private static String normalize(String type) {
        return type == null ? "" : type.trim().toUpperCase(Locale.ROOT);
    }

    private static int parsePort(String port) {
        try {
            return port == null || port.isBlank() ? 587 : Integer.parseInt(port.trim());
        } catch (NumberFormatException ex) {
            return 587;
        }
    }
}
