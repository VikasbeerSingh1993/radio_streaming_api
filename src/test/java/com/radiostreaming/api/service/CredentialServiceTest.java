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
}
