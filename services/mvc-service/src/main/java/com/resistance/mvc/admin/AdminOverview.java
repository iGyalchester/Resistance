package com.resistance.mvc.admin;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * The ops view of the whole deployment: how much is tracked, whether
 * intake is flowing, whether parsing is keeping up, and what the login
 * flow and the assistant have been doing since this process started.
 * Aggregates only - no per-user rows, no PII.
 */
public record AdminOverview(
        int accounts,
        int applications,
        Map<String, Integer> applicationsByStatus,
        List<DayCount> intakeEventsLast30Days,
        List<DayCount> manualEventsLast30Days,
        /** applications with no position title ÷ all; null when nothing is tracked */
        Double unparsedTitleShare,
        AssistantStats assistant,
        AuthStats auth,
        /** when the counters below started from zero (this process's start) */
        Instant countersSince) {

    public record DayCount(LocalDate day, int count) {
    }

    public record AssistantStats(long messages, long inputTokens, long outputTokens,
                                 long refusals, long errors, long throttled) {
    }

    public record AuthStats(long otpRequested, long otpThrottled, long loginSuccess, long loginFailure) {
    }
}
