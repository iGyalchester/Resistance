package com.resistance.shared.utils;

import java.util.Locale;

/**
 * Normalization helpers for person names and email addresses.
 */
public final class NameUtils {

    private NameUtils() {
    }

    /**
     * Capitalizes the first letter and lowercases the rest ("mARY" -> "Mary").
     */
    public static String toTitleCase(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }
        String trimmed = value.trim();
        return Character.toUpperCase(trimmed.charAt(0))
                + trimmed.substring(1).toLowerCase(Locale.ROOT);
    }

    public static String normalizeEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }
}
