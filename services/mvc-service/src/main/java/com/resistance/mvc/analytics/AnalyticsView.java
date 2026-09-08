package com.resistance.mvc.analytics;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Everything the dashboard shows, computed server-side from one account's
 * applications and status history. Null rates mean "not enough data" -
 * the page shows a dash, never a 0% that looks like a result.
 */
public record AnalyticsView(
        int total,
        int active,
        Map<String, Integer> countsByStatus,
        Double responseRate,
        Double offerRate,
        Double medianDaysToFirstResponse,
        Map<String, Double> medianDaysInStage,
        List<WeekBucket> weeklyApplications,
        List<StaleApplication> stale,
        List<Activity> recentActivity) {

    /** Applications created in the ISO week starting on weekStart (a Monday). */
    public record WeekBucket(LocalDate weekStart, int created) {
    }

    /** Still waiting and quiet for a while - the "chase or let go" list. */
    public record StaleApplication(int id, String companyName, String positionTitle,
                                   String status, Integer contactId, long daysSinceChange) {
    }

    /** One status change across the account, newest first in the list. */
    public record Activity(int applicationId, String companyName, String fromStatus,
                           String toStatus, Instant changedAt, String source) {
    }
}
