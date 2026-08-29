package com.resistance.etl.core;

/**
 * Validates a single record before it is transformed and loaded.
 * Implementations live in the etl-validators module.
 */
@FunctionalInterface
public interface RecordValidator<T> {

    ValidationResult validate(T record);

    /** Validator that accepts everything; used when no validation is configured. */
    static <T> RecordValidator<T> acceptAll() {
        return record -> ValidationResult.ok();
    }
}
