package com.resistance.etl.core;

import java.util.Collections;
import java.util.List;

public final class ValidationResult {

    private static final ValidationResult OK = new ValidationResult(List.of());

    private final List<String> errors;

    private ValidationResult(List<String> errors) {
        this.errors = List.copyOf(errors);
    }

    public static ValidationResult ok() {
        return OK;
    }

    public static ValidationResult invalid(List<String> errors) {
        if (errors == null || errors.isEmpty()) {
            throw new IllegalArgumentException("invalid result requires at least one error");
        }
        return new ValidationResult(errors);
    }

    public boolean isValid() {
        return errors.isEmpty();
    }

    public List<String> getErrors() {
        return Collections.unmodifiableList(errors);
    }
}
