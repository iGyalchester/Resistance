package com.resistance.shared.models.entity;

import com.resistance.shared.utils.crypto.FieldEncryptors;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * Transparently encrypts annotated PII columns using the process-wide
 * FieldEncryptor. In dev (no key configured) this is a passthrough; in qa
 * the KMS-sourced data key makes it AES-256-GCM at rest.
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    @Override
    public String convertToDatabaseColumn(String attribute) {
        return FieldEncryptors.active().encrypt(attribute);
    }

    @Override
    public String convertToEntityAttribute(String dbData) {
        return FieldEncryptors.active().decrypt(dbData);
    }
}
