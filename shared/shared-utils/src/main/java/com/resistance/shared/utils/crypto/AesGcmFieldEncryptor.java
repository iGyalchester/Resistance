package com.resistance.shared.utils.crypto;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-256-GCM field encryption. The data key is generated outside the app
 * (Terraform, stored KMS-encrypted in SSM Parameter Store) and injected as
 * base64 through the environment - the application never creates or
 * persists key material.
 * Values are stored as "enc:v1:" + base64(iv || ciphertext+tag); anything
 * without that prefix is treated as legacy plaintext and passed through,
 * so enabling encryption does not break existing rows.
 */
public class AesGcmFieldEncryptor implements FieldEncryptor {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecretKeySpec key;
    private final SecureRandom random = new SecureRandom();

    public AesGcmFieldEncryptor(byte[] keyBytes) {
        if (keyBytes.length != 16 && keyBytes.length != 24 && keyBytes.length != 32) {
            throw new IllegalArgumentException(
                    "AES key must be 16, 24 or 32 bytes; got " + keyBytes.length);
        }
        this.key = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String encrypt(String plaintext) {
        if (plaintext == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

            byte[] combined = new byte[iv.length + ciphertext.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(ciphertext, 0, combined, iv.length, ciphertext.length);

            return PREFIX + Base64.getEncoder().encodeToString(combined);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Field encryption failed", e);
        }
    }

    @Override
    public String decrypt(String stored) {
        if (stored == null || !stored.startsWith(PREFIX)) {
            return stored; // legacy plaintext row
        }
        try {
            byte[] combined = Base64.getDecoder().decode(stored.substring(PREFIX.length()));

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key,
                    new GCMParameterSpec(TAG_BITS, combined, 0, IV_BYTES));
            byte[] plaintext = cipher.doFinal(combined, IV_BYTES, combined.length - IV_BYTES);

            return new String(plaintext, StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Field decryption failed - wrong key?", e);
        }
    }
}
