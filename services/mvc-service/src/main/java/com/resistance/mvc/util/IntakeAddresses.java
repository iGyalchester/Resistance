package com.resistance.mvc.util;

/**
 * Builds the personal intake address shown to a user:
 * "track@domain" + alias "a8f3k2xq99" -> "track+a8f3k2xq99@domain".
 * Shared by the Thymeleaf dashboard and the JSON API.
 */
public final class IntakeAddresses {

    private IntakeAddresses() {
    }

    /** Returns null when the account has no alias yet or the base address is malformed. */
    public static String personal(String baseAddress, String alias) {
        int at = baseAddress == null ? -1 : baseAddress.indexOf('@');
        if (alias == null || alias.isBlank() || at < 0) {
            return null;
        }
        return baseAddress.substring(0, at) + "+" + alias + baseAddress.substring(at);
    }
}
