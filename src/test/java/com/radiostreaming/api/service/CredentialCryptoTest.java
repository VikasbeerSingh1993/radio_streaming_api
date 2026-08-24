package com.radiostreaming.api.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CredentialCryptoTest {

    @Test
    void encryptsAndDecryptsSecretValues() {
        CredentialCrypto crypto = new CredentialCrypto("test-encrypt-key");
        String encrypted = crypto.encrypt("smtp-app-password");

        assertTrue(encrypted.startsWith(CredentialCrypto.PREFIX));
        assertNotEquals("smtp-app-password", encrypted);
        assertEquals("smtp-app-password", crypto.decrypt(encrypted));
    }

    @Test
    void doesNotDoubleEncrypt() {
        CredentialCrypto crypto = new CredentialCrypto("test-encrypt-key");
        String encrypted = crypto.encrypt("secret");
        assertEquals(encrypted, crypto.encrypt(encrypted));
    }
}
