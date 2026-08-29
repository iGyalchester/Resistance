package com.resistance.shared.utils.crypto;

/**
 * Encrypts/decrypts individual PII field values before they hit the
 * database. Implementations must be able to recognize their own output so
 * that plaintext written before encryption was enabled still reads back.
 */
public interface FieldEncryptor {

    String encrypt(String plaintext);

    String decrypt(String stored);

    /** Passthrough used when no encryption key is configured (dev mode). */
    FieldEncryptor NOOP = new FieldEncryptor() {
        @Override
        public String encrypt(String plaintext) {
            return plaintext;
        }

        @Override
        public String decrypt(String stored) {
            return stored;
        }
    };
}
