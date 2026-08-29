package com.resistance.shared.utils.crypto;

import org.junit.jupiter.api.Test;

import java.security.SecureRandom;

import static org.junit.jupiter.api.Assertions.*;

class AesGcmFieldEncryptorTests {

    private static byte[] key() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        return key;
    }

    @Test
    void roundTripsAndNeverStoresPlaintext() {
        AesGcmFieldEncryptor encryptor = new AesGcmFieldEncryptor(key());

        String stored = encryptor.encrypt("+1 555 0100");
        assertTrue(stored.startsWith("enc:v1:"));
        assertFalse(stored.contains("555"));
        assertEquals("+1 555 0100", encryptor.decrypt(stored));
    }

    @Test
    void sameValueEncryptsDifferentlyEachTime() {
        AesGcmFieldEncryptor encryptor = new AesGcmFieldEncryptor(key());
        assertNotEquals(encryptor.encrypt("secret"), encryptor.encrypt("secret"));
    }

    @Test
    void legacyPlaintextPassesThroughOnDecrypt() {
        AesGcmFieldEncryptor encryptor = new AesGcmFieldEncryptor(key());
        assertEquals("+1 555 0100", encryptor.decrypt("+1 555 0100"));
        assertNull(encryptor.decrypt(null));
        assertNull(encryptor.encrypt(null));
    }

    @Test
    void wrongKeyFailsLoudlyInsteadOfReturningGarbage() {
        String stored = new AesGcmFieldEncryptor(key()).encrypt("secret");
        AesGcmFieldEncryptor other = new AesGcmFieldEncryptor(key());
        assertThrows(IllegalStateException.class, () -> other.decrypt(stored));
    }

    @Test
    void rejectsInvalidKeyLength() {
        assertThrows(IllegalArgumentException.class, () -> new AesGcmFieldEncryptor(new byte[10]));
    }
}
