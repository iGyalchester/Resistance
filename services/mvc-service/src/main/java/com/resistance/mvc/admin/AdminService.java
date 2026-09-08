package com.resistance.mvc.admin;

import com.resistance.mvc.auth.AuthMetrics;
import com.resistance.mvc.dao.JobApplicationRepository;
import com.resistance.mvc.dao.StatusHistoryRepository;
import com.resistance.mvc.dao.UserAccountRepository;
import com.resistance.shared.models.entity.ApplicationStatus;
import com.resistance.shared.models.entity.JobApplication;
import com.resistance.shared.models.entity.StatusHistory;
import com.resistance.shared.models.entity.UserAccount;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Builds the admin numbers. Like AnalyticsService the arithmetic is a
 * pure function over lists and a clock ({@link #overviewOf}) so it is
 * table-testable; the repository and meter reads are the thin shell
 * around it. Counters come straight from the Micrometer registry, which
 * is per process: they reset on restart, and the overview carries the
 * start time so the page can say "since".
 */
@Service
public class AdminService {

    static final int DAYS = 30;

    private final UserAccountRepository accounts;
    private final JobApplicationRepository applications;
    private final StatusHistoryRepository history;
    private final MeterRegistry meters;
    private final Clock clock;
    private final Instant startedAt;

    public AdminService(UserAccountRepository accounts, JobApplicationRepository applications,
                        StatusHistoryRepository history, MeterRegistry meters, Clock clock) {
        this.accounts = accounts;
        this.applications = applications;
        this.history = history;
        this.meters = meters;
        this.clock = clock;
        this.startedAt = clock.instant();
    }

    public AdminOverview overview() {
        Instant now = clock.instant();
        Instant since = now.minusSeconds((DAYS - 1) * 86_400L).atOffset(ZoneOffset.UTC)
                .toLocalDate().atStartOfDay().toInstant(ZoneOffset.UTC);
        return overviewOf(accounts.findAll(), applications.findAll(),
                history.findByChangedAtAfterOrderByChangedAtAsc(since), assistantStats(), authStats(), startedAt, now);
    }

    public List<AdminAccountView> accounts() {
        return accountsOf(accounts.findAll(), applications.findAll());
    }

    static AdminOverview overviewOf(List<UserAccount> accounts, List<JobApplication> apps,
                                    List<StatusHistory> recent, AdminOverview.AssistantStats assistant,
                                    AdminOverview.AuthStats auth, Instant startedAt, Instant now) {
        Map<String, Integer> byStatus = new LinkedHashMap<>();
        for (ApplicationStatus status : ApplicationStatus.values()) {
            byStatus.put(status.name(), 0);
        }
        int untitled = 0;
        for (JobApplication app : apps) {
            String status = app.getStatus() == null ? ApplicationStatus.APPLIED.name() : app.getStatus().name();
            byStatus.merge(status, 1, Integer::sum);
            if (app.getPositionTitle() == null || app.getPositionTitle().isBlank()) {
                untitled++;
            }
        }

        LocalDate today = now.atOffset(ZoneOffset.UTC).toLocalDate();
        LocalDate first = today.minusDays(DAYS - 1);
        Map<LocalDate, Integer> intake = new HashMap<>();
        Map<LocalDate, Integer> manual = new HashMap<>();
        for (StatusHistory change : recent) {
            if (change.getChangedAt() == null) {
                continue;
            }
            LocalDate day = change.getChangedAt().atOffset(ZoneOffset.UTC).toLocalDate();
            if (day.isBefore(first) || day.isAfter(today)) {
                continue;
            }
            (StatusHistory.SOURCE_INTAKE.equals(change.getSource()) ? intake : manual).merge(day, 1, Integer::sum);
        }
        List<AdminOverview.DayCount> intakeDays = new ArrayList<>();
        List<AdminOverview.DayCount> manualDays = new ArrayList<>();
        for (LocalDate day = first; !day.isAfter(today); day = day.plusDays(1)) {
            intakeDays.add(new AdminOverview.DayCount(day, intake.getOrDefault(day, 0)));
            manualDays.add(new AdminOverview.DayCount(day, manual.getOrDefault(day, 0)));
        }

        Double unparsed = apps.isEmpty() ? null : (double) untitled / apps.size();
        return new AdminOverview(accounts.size(), apps.size(), byStatus, intakeDays, manualDays,
                unparsed, assistant, auth, startedAt);
    }

    static List<AdminAccountView> accountsOf(List<UserAccount> accounts, List<JobApplication> apps) {
        Map<Integer, Long> counts = new HashMap<>();
        Map<Integer, Instant> latest = new HashMap<>();
        for (JobApplication app : apps) {
            if (app.getOwner() == null) {
                continue;
            }
            int owner = app.getOwner().getId();
            counts.merge(owner, 1L, Long::sum);
            if (app.getUpdatedAt() != null) {
                latest.merge(owner, app.getUpdatedAt(), (a, b) -> a.isAfter(b) ? a : b);
            }
        }
        List<AdminAccountView> views = new ArrayList<>();
        for (UserAccount account : accounts) {
            views.add(new AdminAccountView(account.getId(), account.getEmail(), account.getFullName(),
                    counts.getOrDefault(account.getId(), 0L), latest.get(account.getId()),
                    account.getIntakeAlias() != null && !account.getIntakeAlias().isBlank()));
        }
        views.sort(Comparator.comparing((AdminAccountView v) -> v.lastActivity() == null ? Instant.EPOCH : v.lastActivity())
                .reversed().thenComparing(AdminAccountView::email, String.CASE_INSENSITIVE_ORDER));
        return views;
    }

    private AdminOverview.AssistantStats assistantStats() {
        return new AdminOverview.AssistantStats(count("assistant.messages"), count("assistant.tokens.input"),
                count("assistant.tokens.output"), count("assistant.refusals"), count("assistant.errors"),
                count("assistant.throttled"));
    }

    private AdminOverview.AuthStats authStats() {
        return new AdminOverview.AuthStats(count(AuthMetrics.OTP_REQUESTED), count(AuthMetrics.OTP_THROTTLED),
                count(AuthMetrics.LOGIN_SUCCESS), count(AuthMetrics.LOGIN_FAILURE));
    }

    /** A counter that was never touched does not exist yet; read it as zero. */
    private long count(String name) {
        Counter counter = meters.find(name).counter();
        return counter == null ? 0L : Math.round(counter.count());
    }
}
