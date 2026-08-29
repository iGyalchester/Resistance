package com.resistance.shared.utils.crypto;

/**
 * Process-wide active encryptor, consulted by the JPA attribute converter
 * in shared-models. Each application configures it once at startup (see
 * the EncryptionConfig classes); services that never call use() run with
 * the NOOP passthrough, which leaves values untouched.
 */
public final class FieldEncryptors {

    private static volatile FieldEncryptor active = FieldEncryptor.NOOP;

    private FieldEncryptors() {
    }

    public static void use(FieldEncryptor encryptor) {
        active = encryptor == null ? FieldEncryptor.NOOP : encryptor;
    }

    public static FieldEncryptor active() {
        return active;
    }
}
