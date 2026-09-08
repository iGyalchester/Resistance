package com.resistance.mvc.api;

import com.resistance.mvc.admin.AdminService;
import com.resistance.shared.utils.audit.AuditEventClient;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * The ops dashboard's data. SecurityConfig already requires ROLE_ADMIN
 * for /api/admin/**; the check here repeats it so the rule is visible
 * where the data is handed out and holds even in a test with no filter
 * chain. Every read is audited under the admin's own email - looking at
 * everyone's accounts is exactly the kind of access worth a record.
 */
@RestController
@RequestMapping("/api/admin")
public class AdminApiController {

    private final AdminService admin;
    private final AuditEventClient audit;

    public AdminApiController(AdminService admin, AuditEventClient auditEventClient) {
        this.admin = admin;
        this.audit = auditEventClient;
    }

    @GetMapping("/overview")
    public ResponseEntity<Object> overview() {
        String who = adminEmail();
        if (who == null) {
            return forbidden();
        }
        audit.emit("FILE_ACCESS", "ADMIN_OVERVIEW", who, "admin", null);
        return ResponseEntity.ok(admin.overview());
    }

    @GetMapping("/accounts")
    public ResponseEntity<Object> accounts() {
        String who = adminEmail();
        if (who == null) {
            return forbidden();
        }
        audit.emit("FILE_ACCESS", "ADMIN_ACCOUNTS", who, "admin", null);
        return ResponseEntity.ok(admin.accounts());
    }

    /** The caller's email when they hold ROLE_ADMIN, else null. */
    static String adminEmail() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        boolean admin = auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        return admin ? String.valueOf(auth.getPrincipal()) : null;
    }

    private static ResponseEntity<Object> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(Map.of("error", "forbidden"));
    }
}
