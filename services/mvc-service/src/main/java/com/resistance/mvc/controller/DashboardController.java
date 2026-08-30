package com.resistance.mvc.controller;

import com.resistance.mvc.auth.LoginController;
import com.resistance.mvc.dao.JobApplicationRepository;
import com.resistance.mvc.dao.UserAccountRepository;
import com.resistance.shared.models.entity.UserAccount;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * The logged-in user's view: only their own applications. Access is
 * enforced by DashboardInterceptor.
 */
@Controller
public class DashboardController {

    private final UserAccountRepository accountRepository;
    private final JobApplicationRepository applicationRepository;
    private final String intakeBaseAddress;

    public DashboardController(UserAccountRepository accountRepository,
                               JobApplicationRepository applicationRepository,
                               @Value("${tracker.intake.address:track@resistance.example}") String intakeBaseAddress) {
        this.accountRepository = accountRepository;
        this.applicationRepository = applicationRepository;
        this.intakeBaseAddress = intakeBaseAddress;
    }

    @GetMapping("/dashboard")
    public String dashboard(HttpSession session, Model model) {
        int accountId = (Integer) session.getAttribute(LoginController.SESSION_ACCOUNT_ID);

        UserAccount account = accountRepository.findById(accountId).orElse(null);
        if (account == null) {
            session.invalidate();
            return "redirect:/login";
        }

        model.addAttribute("account", account);
        model.addAttribute("applications", applicationRepository.findByOwnerId(accountId));
        model.addAttribute("intakeAddress", personalIntakeAddress(account.getIntakeAlias()));
        return "dashboard";
    }

    // "track@domain" + alias "a8f3k2xq99" -> "track+a8f3k2xq99@domain"
    private String personalIntakeAddress(String alias) {
        int at = intakeBaseAddress.indexOf('@');
        if (alias == null || alias.isBlank() || at < 0) {
            return null;
        }
        return intakeBaseAddress.substring(0, at) + "+" + alias + intakeBaseAddress.substring(at);
    }
}
