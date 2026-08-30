package com.resistance.mvc.auth;

import com.resistance.shared.models.entity.UserAccount;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Turns a verified account into an authenticated session - the one place
 * that knows the full sequence: rotate the session id (fixation
 * protection), stamp the app-level accountId attribute, and store a
 * Spring Security context where SecurityConfig's authenticated() rule
 * finds it. Used by both the HTML login flow (LoginController) and the
 * JSON one (AuthApiController) so the two can never drift apart.
 */
@Component
public class SessionAuthenticator {

    private final SecurityContextRepository securityContextRepository;

    public SessionAuthenticator(SecurityContextRepository securityContextRepository) {
        this.securityContextRepository = securityContextRepository;
    }

    public void establish(UserAccount account, HttpServletRequest request, HttpServletResponse response) {
        // ensure a session exists before rotating its id - the JSON login
        // may be the very first request of the visit
        request.getSession();
        request.changeSessionId();
        request.getSession().setAttribute(LoginController.SESSION_ACCOUNT_ID, account.getId());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                account.getEmail(), null, List.of(new SimpleGrantedAuthority("ROLE_USER"))));
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
