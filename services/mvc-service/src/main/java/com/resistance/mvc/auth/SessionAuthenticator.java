package com.resistance.mvc.auth;

import com.resistance.shared.models.entity.UserAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Turns a verified account into an authenticated session - the one place
 * that knows the full sequence: rotate the session id (fixation
 * protection), stamp the app-level accountId attribute, and store a
 * Spring Security context where SecurityConfig's authenticated() rule
 * finds it. The JSON login (AuthApiController) is its only caller now
 * that the server-rendered login pages are gone, but it stays a separate
 * piece so the sequence is testable on its own. The authorities come
 * from AdminRoles: USER for everyone, ADMIN for the configured allow-list.
 */
@Component
public class SessionAuthenticator {

    /** Session attribute holding the signed-in account's id - how every API controller learns who calls. */
    public static final String SESSION_ACCOUNT_ID = "accountId";

    private final SecurityContextRepository securityContextRepository;
    private final AdminRoles adminRoles;

    public SessionAuthenticator(SecurityContextRepository securityContextRepository, AdminRoles adminRoles) {
        this.securityContextRepository = securityContextRepository;
        this.adminRoles = adminRoles;
    }

    public void establish(UserAccount account, HttpServletRequest request, HttpServletResponse response) {
        // ensure a session exists before rotating its id - the JSON login
        // may be the very first request of the visit
        request.getSession();
        request.changeSessionId();
        request.getSession().setAttribute(SESSION_ACCOUNT_ID, account.getId());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                account.getEmail(), null, adminRoles.authoritiesFor(account.getEmail())));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
