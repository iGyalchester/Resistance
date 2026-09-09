package com.resistance.mvc.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Who is an admin: an allow-list of email addresses from configuration
 * (env TRACKER_ADMIN_EMAILS, comma-separated, case-insensitive). No
 * schema change and no in-app role management - for a deployment with a
 * handful of admins a config value is easier to audit than a table, and
 * a column can come later without touching the callers of this class.
 */
@Component
public class AdminRoles {

    private final Set<String> admins;

    public AdminRoles(@Value("${tracker.admin.emails:}") String emails) {
        this.admins = emails == null ? Set.of() : Arrays.stream(emails.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(String email) {
        return email != null && admins.contains(email.trim().toLowerCase(Locale.ROOT));
    }

    /** Every signed-in account is a USER; listed ones are ADMIN as well. */
    public List<GrantedAuthority> authoritiesFor(String email) {
        return isAdmin(email)
                ? List.of(new SimpleGrantedAuthority("ROLE_USER"), new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of(new SimpleGrantedAuthority("ROLE_USER"));
    }
}
