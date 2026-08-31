package com.resistance.mvc.auth;

import com.resistance.shared.models.entity.UserAccount;
import com.resistance.shared.utils.audit.AuditEventClient;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/**
 * Passwordless login: email -> one-time code -> authenticated session.
 * On success the session id is rotated (fixation protection) and a
 * Spring Security context is stored, which is what SecurityConfig's
 * authenticated() rule checks. The "accountId" session attribute is the
 * app-level identity used by controllers.
 */
@Controller
public class LoginController {

    public static final String SESSION_ACCOUNT_ID = "accountId";
    private static final String SESSION_LOGIN_EMAIL = "loginEmail";

    private static final Logger log = LoggerFactory.getLogger(LoginController.class);

    private final OtpService otpService;
    private final SessionAuthenticator sessionAuthenticator;
    private final OtpRequestThrottle emailThrottle;
    private final OtpRequestThrottle ipThrottle;
    private final AuditEventClient audit;

    public LoginController(OtpService otpService,
                           SessionAuthenticator sessionAuthenticator,
                           OtpRequestThrottle emailOtpThrottle,
                           OtpRequestThrottle ipOtpThrottle,
                           AuditEventClient auditEventClient) {
        this.otpService = otpService;
        this.sessionAuthenticator = sessionAuthenticator;
        this.emailThrottle = emailOtpThrottle;
        this.ipThrottle = ipOtpThrottle;
        this.audit = auditEventClient;
    }

    @GetMapping("/login")
    public String showEmailForm() {
        return "login-email";
    }

    @PostMapping("/login")
    public String requestCode(@RequestParam("email") String email,
                              HttpServletRequest request, HttpSession session) {

        // throttled requests get the exact same response as successful
        // ones - no signal for enumeration or probing
        boolean allowed = emailThrottle.tryAcquire("email:" + email.trim().toLowerCase())
                && ipThrottle.tryAcquire("ip:" + request.getRemoteAddr());
        if (allowed) {
            otpService.requestCode(email);
        } else {
            log.warn("OTP request throttled for {}", request.getRemoteAddr());
        }

        audit.emit("AUTH_EVENT", "OTP_REQUESTED", email.trim().toLowerCase(),
                "login", request.getRemoteAddr());
        session.setAttribute(SESSION_LOGIN_EMAIL, email.trim());
        return "redirect:/login/code";
    }

    @GetMapping("/login/code")
    public String showCodeForm(HttpSession session, Model model) {
        String email = (String) session.getAttribute(SESSION_LOGIN_EMAIL);
        if (email == null) {
            return "redirect:/login";
        }
        model.addAttribute("email", email);
        return "login-code";
    }

    @PostMapping("/login/code")
    public String verifyCode(@RequestParam("code") String code,
                             HttpServletRequest request, HttpServletResponse response,
                             Model model) {
        HttpSession session = request.getSession();
        String email = (String) session.getAttribute(SESSION_LOGIN_EMAIL);
        if (email == null) {
            return "redirect:/login";
        }

        Optional<UserAccount> account = otpService.verify(email, code);
        if (account.isEmpty()) {
            audit.emit("AUTH_EVENT", "LOGIN_FAILURE", email.toLowerCase(),
                    "login", request.getRemoteAddr());
            model.addAttribute("email", email);
            model.addAttribute("loginError", "That code didn't work. Check it or request a new one.");
            return "login-code";
        }

        session.removeAttribute(SESSION_LOGIN_EMAIL);
        sessionAuthenticator.establish(account.get(), request, response);
        audit.emit("AUTH_EVENT", "LOGIN_SUCCESS", account.get().getEmail(),
                "login", request.getRemoteAddr());

        return "redirect:/dashboard";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        SecurityContextHolder.clearContext();
        session.invalidate();
        return "redirect:/login";
    }
}
