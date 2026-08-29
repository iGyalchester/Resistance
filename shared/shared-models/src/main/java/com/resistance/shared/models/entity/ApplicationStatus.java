package com.resistance.shared.models.entity;

import java.util.Locale;
import java.util.Optional;

/**
 * Lifecycle of a tracked job application. Persisted by name
 * (EnumType.STRING), so renaming a constant is a data migration.
 */
public enum ApplicationStatus {

    APPLIED,
    SCREENING,
    INTERVIEW,
    OFFER,
    REJECTED,
    ACCEPTED,
    WITHDRAWN;

    /**
     * Case-insensitive parse for raw input (CSV imports, forms).
     */
    public static Optional<ApplicationStatus> fromString(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        try {
            return Optional.of(valueOf(value.trim().toUpperCase(Locale.ROOT)));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
