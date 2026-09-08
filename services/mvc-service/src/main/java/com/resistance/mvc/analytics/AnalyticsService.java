package com.resistance.mvc.analytics;

import com.resistance.mvc.dao.JobApplicationRepository;
import com.resistance.mvc.dao.StatusHistoryRepository;
import com.resistance.shared.models.entity.ApplicationStatus;
import com.resistance.shared.models.entity.JobApplication;
import com.resistance.shared.models.entity.StatusHistory;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Turns one account's applications and their status history into the
 * dashboard's numbers. The arithmetic lives in {@link #compute}, a pure
 * function over lists and a clock, so every rule has a table-driven test
 * with no database behind it. Medians rather than averages throughout:
 * one application that sat for 200 days should not drag "typical" up.
 */
@Service
public class AnalyticsService {

    static final int STALE_AFTER_DAYS = 14;
    static final int WEEKS = 12;
    static final int RECENT_ACTIVITY = 10;

    private static final Set<ApplicationStatus> ACTIVE =
            EnumSet.of(ApplicationStatus.APPLIED, ApplicationStatus.SCREENING,
                    ApplicationStatus.INTERVIEW, ApplicationStatus.OFFER);
    private static final Set<ApplicationStatus> STALE_CANDIDATES =
            EnumSet.of(ApplicationStatus.APPLIED, ApplicationStatus.SCREENING);
    private static final Set<ApplicationStatus> OFFERED =
            EnumSet.of(ApplicationStatus.OFFER, ApplicationStatus.ACCEPTED);

    private final JobApplicationRepository applications;
    private final StatusHistoryRepository history;
    private final Clock clock;

    public AnalyticsService(JobApplicationRepository applications, StatusHistoryRepository history, Clock clock) {
        this.applications = applications;
        this.history = history;
        this.clock = clock;
    }

    /** Owner-scoped: both queries take the account id; nothing else is read. */
    public AnalyticsView forOwner(int ownerId) {
        return compute(applications.findByOwnerId(ownerId),
                history.findByApplicationOwnerIdOrderByChangedAtAsc(ownerId), clock.instant());
    }

    static AnalyticsView compute(List<JobApplication> apps, List<StatusHistory> changes, Instant now) {
        Map<Integer, List<StatusHistory>> byApp = changes.stream()
                .collect(Collectors.groupingBy(h -> h.getApplication().getId(), LinkedHashMap::new,
                        Collectors.toList()));

        // counts per status, every status present, in enum order
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (ApplicationStatus s : ApplicationStatus.values()) {
            counts.put(s.name(), 0);
        }
        int active = 0;
        for (JobApplication app : apps) {
            if (app.getStatus() != null) {
                counts.merge(app.getStatus().name(), 1, Integer::sum);
                if (ACTIVE.contains(app.getStatus())) {
                    active++;
                }
            }
        }

        // response = ever left APPLIED; offer = ever reached OFFER/ACCEPTED
        int responded = 0;
        int offered = 0;
        List<Double> daysToFirstResponse = new ArrayList<>();
        Map<ApplicationStatus, List<Double>> stays = new EnumMap<>(ApplicationStatus.class);
        for (JobApplication app : apps) {
            List<StatusHistory> rows = byApp.getOrDefault(app.getId(), List.of());
            boolean everResponded = rows.stream()
                    .anyMatch(h -> h.getFromStatus() != null && h.getToStatus() != ApplicationStatus.APPLIED)
                    || (app.getStatus() != null && app.getStatus() != ApplicationStatus.APPLIED);
            if (everResponded) {
                responded++;
            }
            boolean everOffered = rows.stream().anyMatch(h -> OFFERED.contains(h.getToStatus()))
                    || (app.getStatus() != null && OFFERED.contains(app.getStatus()));
            if (everOffered) {
                offered++;
            }

            Instant created = rows.stream().filter(h -> h.getFromStatus() == null).findFirst()
                    .map(StatusHistory::getChangedAt).orElse(app.getAppliedAt());
            rows.stream()
                    .filter(h -> h.getFromStatus() != null && h.getToStatus() != ApplicationStatus.APPLIED)
                    .findFirst()
                    .ifPresent(first -> {
                        if (created != null) {
                            daysToFirstResponse.add(days(created, first.getChangedAt()));
                        }
                    });

            // time in stage: each completed stay is (leave time - enter time)
            Instant enteredAt = created;
            ApplicationStatus stage = rows.isEmpty() ? null : rows.get(0).getToStatus();
            for (StatusHistory h : rows) {
                if (h.getFromStatus() == null) {
                    enteredAt = h.getChangedAt();
                    stage = h.getToStatus();
                    continue;
                }
                if (stage != null && enteredAt != null) {
                    stays.computeIfAbsent(stage, k -> new ArrayList<>()).add(days(enteredAt, h.getChangedAt()));
                }
                enteredAt = h.getChangedAt();
                stage = h.getToStatus();
            }
        }

        Map<String, Double> medianStays = new LinkedHashMap<>();
        for (ApplicationStatus s : ApplicationStatus.values()) {
            List<Double> list = stays.get(s);
            if (list != null && !list.isEmpty()) {
                medianStays.put(s.name(), round1(median(list)));
            }
        }

        // weekly creations, last WEEKS ISO weeks including the current one, zero-filled
        LocalDate today = LocalDate.ofInstant(now, ZoneOffset.UTC);
        LocalDate thisWeek = today.with(DayOfWeek.MONDAY);
        Map<LocalDate, Integer> weekly = new LinkedHashMap<>();
        for (int i = WEEKS - 1; i >= 0; i--) {
            weekly.put(thisWeek.minusWeeks(i), 0);
        }
        for (JobApplication app : apps) {
            Instant created = byApp.getOrDefault(app.getId(), List.of()).stream()
                    .filter(h -> h.getFromStatus() == null).findFirst()
                    .map(StatusHistory::getChangedAt).orElse(app.getAppliedAt());
            if (created == null) {
                continue;
            }
            LocalDate week = LocalDate.ofInstant(created, ZoneOffset.UTC).with(DayOfWeek.MONDAY);
            if (weekly.containsKey(week)) {
                weekly.merge(week, 1, Integer::sum);
            }
        }
        List<AnalyticsView.WeekBucket> buckets = weekly.entrySet().stream()
                .map(e -> new AnalyticsView.WeekBucket(e.getKey(), e.getValue()))
                .toList();

        // stale: still waiting, and nothing has happened for STALE_AFTER_DAYS
        List<AnalyticsView.StaleApplication> stale = new ArrayList<>();
        for (JobApplication app : apps) {
            if (app.getStatus() == null || !STALE_CANDIDATES.contains(app.getStatus())) {
                continue;
            }
            List<StatusHistory> rows = byApp.getOrDefault(app.getId(), List.of());
            Instant last = rows.isEmpty() ? app.getUpdatedAt() : rows.get(rows.size() - 1).getChangedAt();
            if (last == null) {
                last = app.getAppliedAt();
            }
            if (last == null) {
                continue;
            }
            long quietDays = Duration.between(last, now).toDays();
            if (quietDays >= STALE_AFTER_DAYS) {
                stale.add(new AnalyticsView.StaleApplication(app.getId(), app.getCompanyName(),
                        app.getPositionTitle(), app.getStatus().name(),
                        app.getContact() == null ? null : app.getContact().getId(), quietDays));
            }
        }
        stale.sort(Comparator.comparingLong(AnalyticsView.StaleApplication::daysSinceChange).reversed());

        Map<Integer, String> names = new HashMap<>();
        for (JobApplication app : apps) {
            names.put(app.getId(), app.getCompanyName());
        }
        List<AnalyticsView.Activity> recent = changes.stream()
                .sorted(Comparator.comparing(StatusHistory::getChangedAt).reversed())
                .limit(RECENT_ACTIVITY)
                .map(h -> new AnalyticsView.Activity(h.getApplication().getId(),
                        names.getOrDefault(h.getApplication().getId(), h.getApplication().getCompanyName()),
                        h.getFromStatus() == null ? null : h.getFromStatus().name(),
                        h.getToStatus().name(), h.getChangedAt(), h.getSource()))
                .toList();

        int total = apps.size();
        return new AnalyticsView(
                total,
                active,
                counts,
                total == 0 ? null : round2((double) responded / total),
                total == 0 ? null : round2((double) offered / total),
                daysToFirstResponse.isEmpty() ? null : round1(median(daysToFirstResponse)),
                medianStays,
                buckets,
                stale,
                recent);
    }

    private static double days(Instant from, Instant to) {
        return Duration.between(from, to).toMillis() / 86_400_000.0;
    }

    static double median(List<Double> values) {
        List<Double> sorted = values.stream().sorted().toList();
        int n = sorted.size();
        return n % 2 == 1 ? sorted.get(n / 2) : (sorted.get(n / 2 - 1) + sorted.get(n / 2)) / 2.0;
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
