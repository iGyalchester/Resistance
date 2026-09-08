package com.resistance.mvc.api;

import com.resistance.mvc.analytics.AnalyticsService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The dashboard's numbers for the session account. */
@RestController
@RequestMapping("/api/analytics")
public class AnalyticsApiController {

    private final AnalyticsService analytics;

    public AnalyticsApiController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/summary")
    public ResponseEntity<Object> summary(HttpSession session) {
        Integer accountId = ApiSessions.accountId(session);
        if (accountId == null) {
            return ApiSessions.unauthorized();
        }
        return ResponseEntity.ok(analytics.forOwner(accountId));
    }
}
