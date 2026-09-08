package com.resistance.mvc.api;

import com.resistance.mvc.auth.AuthMetrics;
import com.resistance.mvc.auth.OtpRequestThrottle;
import com.resistance.mvc.auth.OtpService;
import com.resistance.mvc.auth.SessionAuthenticator;
import com.resistance.mvc.dao.UserAccountRepository;
import com.resistance.shared.models.entity.UserAccount;
import com.resistance.shared.utils.audit.AuditEventClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The login flow for the React app: OtpService issues and verifies the
 * emailed codes, the throttles cap requests, SessionAuthenticator turns a
 * verified account into the session cookie. Code requests always answer identically
 * (throttled or not, known account or not) so nothing is enumerable.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthApiController {

    private static final Logger log = LoggerFactory.getLogger(AuthApiController.class);

    private final OtpService otpService;
    private final SessionAuthenticator sessionAuthenticator;
    private final OtpRequestThrottle emailThrottle;
    private final OtpRequestThrottle ipThrottle;
    private final UserAccountRepository accountRepository;
    private final String intakeBaseAddress;
    private final AuditEventClient audit;
    private final MeView.Features features;
    private final AuthMetrics metrics;

    public AuthApiController(OtpService otpService,
                             SessionAuthenticator sessionAuthenticator,
                             OtpRequestThrottle emailOtpThrottle,
                             OtpRequestThrottle ipOtpThrottle,
                             UserAccountRepository accountRepository,
                             @Value("${tracker.intake.address:track@resistance.example}") String intakeBaseAddress,
                             AuditEventClient auditEventClient,
                             @Value("${tracker.ai.api-key:}") String assistantApiKey,
                             AuthMetrics authMetrics) {
        this.otpService = otpService;
        this.metrics = authMetrics;
        this.sessionAuthenticator = sessionAuthenticator;
        this.emailThrottle = emailOtpThrottle;
        this.ipThrottle = ipOtpThrottle;
        this.accountRepository = accountRepository;
        this.intakeBaseAddress = intakeBaseAddress;
        this.audit = auditEventClient;
        // the assistant is on exactly when a key is configured; the shell
        // hides the feature otherwise instead of showing a broken page
        this.features = new MeView.Features(assistantApiKey != null && !assistantApiKey.isBlank());
    }

    public record CodeRequest(String email) {
    }

    public record LoginRequest(String email, String code) {
    }

    @PostMapping("/code")
    public ResponseEntity<Map<String, String>> requestCode(@RequestBody CodeRequest body,
                                                           HttpServletRequest request) {
        String email = body.email() == null ? "" : body.email().trim();
        if (email.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email_required"));
        }

        boolean allowed = emailThrottle.tryAcquire("email:" + email.toLowerCase())
                && ipThrottle.tryAcquire("ip:" + request.getRemoteAddr());
        metrics.otpRequested();
        if (allowed) {
            otpService.requestCode(email);
        } else {
            metrics.otpThrottled();
            log.warn("OTP request throttled for {}", request.getRemoteAddr());
        }

        audit.emit("AUTH_EVENT", "OTP_REQUESTED", email.toLowerCase(),
                "login", request.getRemoteAddr());
        // identical answer whether the account exists or the caller is throttled
        return ResponseEntity.ok(Map.of("message", "If that address is known, a code is on its way."));
    }

    @PostMapping("/login")
    public ResponseEntity<Object> login(@RequestBody LoginRequest body,
                                        HttpServletRequest request, HttpServletResponse response) {
        String email = body.email() == null ? "" : body.email().trim();
        String code = body.code() == null ? "" : body.code().trim();
        if (email.isEmpty() || code.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "email_and_code_required"));
        }

        Optional<UserAccount> account = otpService.verify(email, code);
        if (account.isEmpty()) {
            metrics.loginFailure();
            audit.emit("AUTH_EVENT", "LOGIN_FAILURE", email.toLowerCase(),
                    "login", request.getRemoteAddr());
            return ResponseEntity.badRequest().body(Map.of("error", "invalid_code"));
        }

        sessionAuthenticator.establish(account.get(), request, response);
        metrics.loginSuccess();
        audit.emit("AUTH_EVENT", "LOGIN_SUCCESS", account.get().getEmail(),
                "login", request.getRemoteAddr());
        return ResponseEntity.ok(MeView.of(account.get(), intakeBaseAddress, currentRoles(), features));
    }

    @GetMapping("/me")
    public ResponseEntity<Object> me(HttpSession session) {
        Integer accountId = (Integer) session.getAttribute(SessionAuthenticator.SESSION_ACCOUNT_ID);
        UserAccount account = accountId == null ? null
                : accountRepository.findById(accountId).orElse(null);
        if (account == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "unauthenticated"));
        }
        return ResponseEntity.ok(MeView.of(account, intakeBaseAddress, currentRoles(), features));
    }

    /** ROLE_* authorities of the current security context, without the prefix; USER when none. */
    static List<String> currentRoles() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        List<String> roles = auth == null ? List.of() : auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()))
                .toList();
        return roles.isEmpty() ? List.of("USER") : roles;
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpSession session) {
        SecurityContextHolder.clearContext();
        session.invalidate();
        return ResponseEntity.noContent().build();
    }
}
