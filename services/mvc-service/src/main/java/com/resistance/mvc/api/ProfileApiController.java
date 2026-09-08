package com.resistance.mvc.api;

import com.resistance.mvc.dao.UserAccountRepository;
import com.resistance.shared.models.entity.UserAccount;
import com.resistance.shared.utils.audit.AuditEventClient;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * JSON twin of ProfileController: the fields intake cannot learn from
 * emails (name, phone). Email is identity and read-only. Phone is PII
 * encrypted at rest, so reading it is audited just like updating it.
 */
@RestController
@RequestMapping("/api/profile")
public class ProfileApiController {

    private final UserAccountRepository accountRepository;
    private final AuditEventClient audit;

    public ProfileApiController(UserAccountRepository accountRepository, AuditEventClient auditEventClient) {
        this.accountRepository = accountRepository;
        this.audit = auditEventClient;
    }

    @GetMapping
    public ResponseEntity<Object> get(HttpSession session) {
        Integer accountId = ApiSessions.accountId(session);
        if (accountId == null) {
            return ApiSessions.unauthorized();
        }
        UserAccount account = accountRepository.findById(accountId).orElseThrow(NotFoundException::new);
        audit.emit("FILE_ACCESS", "PROFILE_VIEW", account.getEmail(), "user_account:" + accountId, null);
        return ResponseEntity.ok(ProfileView.of(account));
    }

    @PutMapping
    public ResponseEntity<Object> update(@Valid @RequestBody ProfileRequest body, HttpSession session) {
        Integer accountId = ApiSessions.accountId(session);
        if (accountId == null) {
            return ApiSessions.unauthorized();
        }
        UserAccount account = accountRepository.findById(accountId).orElseThrow(NotFoundException::new);
        account.setFullName(body.fullName().trim());
        account.setPhone(body.phone() == null || body.phone().isBlank() ? null : body.phone().trim());
        accountRepository.save(account);
        audit.emit("FILE_ACCESS", "PROFILE_UPDATE", account.getEmail(), "user_account:" + accountId, null);
        return ResponseEntity.ok(ProfileView.of(account));
    }
}
