package com.resistance.mvc.api;

import com.resistance.mvc.auth.LoginController;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

/**
 * The one way an API controller learns who is calling: the accountId the
 * login flow stamped on the session. Anything else (a header, a body
 * field) would let the caller pick an identity.
 */
final class ApiSessions {

    private ApiSessions() {
    }

    /** The session's account id, or null when nobody is logged in. */
    static Integer accountId(HttpSession session) {
        return (Integer) session.getAttribute(LoginController.SESSION_ACCOUNT_ID);
    }

    static <T> ResponseEntity<T> unauthorized() {
        @SuppressWarnings("unchecked")
        ResponseEntity<T> response = (ResponseEntity<T>) ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "unauthenticated"));
        return response;
    }
}
