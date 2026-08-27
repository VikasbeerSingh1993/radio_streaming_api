package com.radiostreaming.api.service;

import com.radiostreaming.api.model.CredentialDocument;
import com.radiostreaming.api.repository.CredentialRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CredentialServiceTest {

    @Mock
    private CredentialRepository repository;

    private CredentialService service;

    @BeforeEach
    void setUp() {
        service = new CredentialService(repository, new CredentialCrypto("test-key"));
        when(repository.findByTypeIgnoreCase(any())).thenReturn(Optional.empty());
        when(repository.save(any(CredentialDocument.class))).thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void seedEncryptsGmailPassword() {
        service.seedIfMissing("GMAIL", Map.of(
                "host", "smtp.gmail.com",
                "port", "587",
                "username", "user@gmail.com",
                "password", "plain-password"
        ));

        ArgumentCaptor<CredentialDocument> captor = ArgumentCaptor.forClass(CredentialDocument.class);
        verify(repository).save(captor.capture());
        String stored = captor.getValue().getFields().get("password");
        assertTrue(stored.startsWith(CredentialCrypto.PREFIX));
        assertEquals("plain-password", new CredentialCrypto("test-key").decrypt(stored));
        assertEquals("smtp.gmail.com", captor.getValue().getFields().get("host"));
    }

    @Test
    void seedEncryptsGeoApiKey() {
        service.seedIfMissing("GEO", Map.of(
                "provider", "countrystatecity",
                "apiKey", "csc-test-key"
        ));

        ArgumentCaptor<CredentialDocument> captor = ArgumentCaptor.forClass(CredentialDocument.class);
        verify(repository).save(captor.capture());
        String stored = captor.getValue().getFields().get("apiKey");
        assertTrue(stored.startsWith(CredentialCrypto.PREFIX));
        assertEquals("csc-test-key", new CredentialCrypto("test-key").decrypt(stored));
        assertEquals("countrystatecity", captor.getValue().getFields().get("provider"));
    }

    @Test
    void ensurePhotonProviderWhenCountryStateCityHasNoKey() {
        service.seedIfMissing("GEO", Map.of(
                "provider", "countrystatecity",
                "apiKey", ""
        ));

        service.ensurePhotonProvider();

        ArgumentCaptor<CredentialDocument> captor = ArgumentCaptor.forClass(CredentialDocument.class);
        verify(repository, org.mockito.Mockito.times(2)).save(captor.capture());
        assertEquals("photon", captor.getValue().getFields().get("provider"));
    }

    @Test
    void ensurePhotonProviderKeepsKeyedCountryStateCity() {
        service.seedIfMissing("GEO", Map.of(
                "provider", "countrystatecity",
                "apiKey", "csc-test-key"
        ));

        service.ensurePhotonProvider();

        verify(repository, org.mockito.Mockito.times(1)).save(any(CredentialDocument.class));
        assertEquals("countrystatecity", service.geoProvider());
    }

    @Test
    void mailSenderRequiresUsernameAndPassword() {
        service.seedIfMissing("GMAIL", Map.of(
                "host", "smtp.gmail.com",
                "port", "587",
                "username", "user@gmail.com",
                "password", "plain-password",
                "from", "user@gmail.com"
        ));
        assertTrue(service.isMailConfigured());
    }

    @Test
    void seedEncryptsMongoPassword() {
        service.seedIfMissing("MONGO", Map.of(
                "username", "atlasUser",
                "password", "atlas-password",
                "cluster", "cluster0.abc123.mongodb.net",
                "database", "divine_bliss_streaming"
        ));

        ArgumentCaptor<CredentialDocument> captor = ArgumentCaptor.forClass(CredentialDocument.class);
        verify(repository).save(captor.capture());
        String stored = captor.getValue().getFields().get("password");
        assertTrue(stored.startsWith(CredentialCrypto.PREFIX));
        assertEquals("atlas-password", new CredentialCrypto("test-key").decrypt(stored));
        assertEquals("cluster0.abc123.mongodb.net", captor.getValue().getFields().get("cluster"));
        assertEquals("divine_bliss_streaming", captor.getValue().getFields().get("database"));
    }
}
