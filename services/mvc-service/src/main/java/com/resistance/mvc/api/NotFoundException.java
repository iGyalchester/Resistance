package com.resistance.mvc.api;

/**
 * Thrown by API controllers when the owner-scoped lookup comes back empty.
 * Whether the row is missing or belongs to someone else is deliberately
 * not distinguishable: both are a 404 (see ApiErrorHandler).
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException() {
        super("not_found");
    }
}
