package com.resistance.mvc.auth;

import com.resistance.shared.models.entity.UserAccount;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Optional;

/**
 * Passwordless login: email -> one-time code -> session. The session
 * attribute "accountId" is what DashboardInterceptor checks.
 */
@Controller
public class LoginController {

    public static final String SESSION_ACCOUNT_ID = "accountId";
    private static final String SESSION_LOGIN_EMAIL = "loginEmail";

    private final OtpService otpService;

    public LoginController(OtpService otpService) {
        this.otpService = otpService;
    }

    @GetMapping("/login")
    public String showEmailForm() {
        return "login-email";
    }

    @PostMapping("/login")
    public String requestCode(@RequestParam("email") String email, HttpSession session) {
        otpService.requestCode(email);
        // same outcome whether or not the account exists - no enumeration
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
    public String verifyCode(@RequestParam("code") String code, HttpSession session, Model model) {
        String email = (String) session.getAttribute(SESSION_LOGIN_EMAIL);
        if (email == null) {
            return "redirect:/login";
        }

        Optional<UserAccount> account = otpService.verify(email, code);
        if (account.isEmpty()) {
            model.addAttribute("email", email);
            model.addAttribute("loginError", "That code didn't work. Check it or request a new one.");
            return "login-code";
        }

        session.removeAttribute(SESSION_LOGIN_EMAIL);
        session.setAttribute(SESSION_ACCOUNT_ID, account.get().getId());
        return "redirect:/dashboard";
    }

    @GetMapping("/logout")
    public String logout(HttpSession session) {
        session.invalidate();
        return "redirect:/login";
    }
}
