package com.resistance.mvc.api;

/** No account on the session. Mapped to 401 by ApiErrorHandler for endpoints that cannot return a ResponseEntity. */
public class UnauthenticatedException extends RuntimeException {

    public UnauthenticatedException() {
        super("unauthenticated");
    }
}
