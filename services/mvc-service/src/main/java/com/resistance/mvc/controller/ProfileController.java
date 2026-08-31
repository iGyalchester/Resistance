package com.resistance.mvc.controller;

import com.resistance.mvc.auth.LoginController;
import com.resistance.mvc.dao.UserAccountRepository;
import com.resistance.shared.models.entity.UserAccount;
import com.resistance.shared.utils.audit.AuditEventClient;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Lets the logged-in user maintain the profile fields intake can't learn
 * from emails (name correction, phone - which is encrypted at rest).
 * Email is identity and not editable here.
 */
@Controller
public class ProfileController {

    private final UserAccountRepository accountRepository;
    private final AuditEventClient audit;

    public ProfileController(UserAccountRepository accountRepository, AuditEventClient auditEventClient) {
        this.accountRepository = accountRepository;
        this.audit = auditEventClient;
    }

    @GetMapping("/profile")
    public String showProfile(HttpSession session, Model model) {
        int accountId = (Integer) session.getAttribute(LoginController.SESSION_ACCOUNT_ID);
        UserAccount account = accountRepository.findById(accountId).orElseThrow();
        // profile holds the encrypted phone - PII access is audit-worthy
        audit.emit("FILE_ACCESS", "PROFILE_VIEW", account.getEmail(),
                "user_account:" + accountId, null);
        model.addAttribute("account", account);
        return "profile";
    }

    @PostMapping("/profile")
    public String saveProfile(@RequestParam("fullName") String fullName,
                              @RequestParam(value = "phone", required = false) String phone,
                              HttpSession session) {
        int accountId = (Integer) session.getAttribute(LoginController.SESSION_ACCOUNT_ID);
        UserAccount account = accountRepository.findById(accountId).orElseThrow();

        account.setFullName(fullName.trim());
        account.setPhone(phone == null || phone.isBlank() ? null : phone.trim());
        accountRepository.save(account);
        audit.emit("FILE_ACCESS", "PROFILE_UPDATE", account.getEmail(),
                "user_account:" + accountId, null);

        return "redirect:/profile";
    }
}
