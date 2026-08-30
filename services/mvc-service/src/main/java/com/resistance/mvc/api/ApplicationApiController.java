package com.resistance.mvc.api;

import com.resistance.mvc.auth.LoginController;
import com.resistance.mvc.service.JobApplicationService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Read side of the tracker for the React app. Owner-scoping lives in
 * JobApplicationService - this controller only supplies the session
 * account id, exactly like the Thymeleaf controllers do.
 */
@RestController
@RequestMapping("/api/applications")
public class ApplicationApiController {

    private final JobApplicationService applicationService;

    public ApplicationApiController(JobApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @GetMapping
    public ResponseEntity<Object> list(HttpSession session) {
        Integer accountId = (Integer) session.getAttribute(LoginController.SESSION_ACCOUNT_ID);
        if (accountId == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(Map.of("error", "unauthenticated"));
        }
        return ResponseEntity.ok(applicationService.findAllForOwner(accountId).stream()
                .map(ApplicationView::of)
                .toList());
    }
}
