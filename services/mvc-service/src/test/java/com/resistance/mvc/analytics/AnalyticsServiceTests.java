package com.resistance.mvc.analytics;

import com.resistance.mvc.dao.JobApplicationRepository;
import com.resistance.mvc.dao.StatusHistoryRepository;
import com.resistance.shared.models.entity.ApplicationStatus;
import com.resistance.shared.models.entity.JobApplication;
import com.resistance.shared.models.entity.StatusHistory;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import static com.resistance.shared.models.entity.ApplicationStatus.APPLIED;
import static com.resistance.shared.models.entity.ApplicationStatus.INTERVIEW;
import static com.resistance.shared.models.entity.ApplicationStatus.OFFER;
import static com.resistance.shared.models.entity.ApplicationStatus.REJECTED;
import static com.resistance.shared.models.entity.ApplicationStatus.SCREENING;
import static com.resistance.shared.models.entity.StatusHistory.SOURCE_INTAKE;
import static com.resistance.shared.models.entity.StatusHistory.SOURCE_MANUAL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The dashboard arithmetic, checked case by case against a fixed clock.
 * Every rule the TECH-GUIDE explains has a case here.
 */
class AnalyticsServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z"); // a Tuesday

    private int nextId = 1;
    private final List<StatusHistory> changes = new ArrayList<>();

    private JobApplication app(String company, ApplicationStatus status, String createdAt, String... transitions) {
        JobApplication app = new JobApplication(company, null, status);
        app.setId(nextId++);
        app.setAppliedAt(Instant.parse(createdAt));
        app.setUpdatedAt(Instant.parse(createdAt));
        changes.add(new StatusHistory(app, null, APPLIED, Instant.parse(createdAt), SOURCE_INTAKE));
        ApplicationStatus from = APPLIED;
        for (String t : transitions) {
            String[] parts = t.split("@");
            ApplicationStatus to = ApplicationStatus.valueOf(parts[0]);
            changes.add(new StatusHistory(app, from, to, Instant.parse(parts[1]), SOURCE_MANUAL));
            from = to;
        }
        return app;
    }

    @Test
    void emptyAccountHasZerosAndNoRates() {
        AnalyticsView v = AnalyticsService.compute(List.of(), List.of(), NOW);

        assertThat(v.total()).isZero();
        assertThat(v.active()).isZero();
        assertThat(v.responseRate()).isNull();
        assertThat(v.offerRate()).isNull();
        assertThat(v.medianDaysToFirstResponse()).isNull();
        assertThat(v.countsByStatus()).containsKeys("APPLIED", "WITHDRAWN").containsValue(0).hasSize(7);
        assertThat(v.weeklyApplications()).hasSize(12).allMatch(w -> w.created() == 0);
        assertThat(v.stale()).isEmpty();
        assertThat(v.recentActivity()).isEmpty();
    }

    @Test
    void funnelCountsAndRatesFollowTheHistory() {
        List<JobApplication> apps = List.of(
                app("Acme", APPLIED, "2026-08-20T09:00:00Z"),
                app("Globex", SCREENING, "2026-08-18T10:30:00Z", "SCREENING@2026-08-24T15:00:00Z"),
                app("Initech", INTERVIEW, "2026-08-12T08:15:00Z", "INTERVIEW@2026-08-26T11:00:00Z"),
                app("Umbrella", OFFER, "2026-08-01T12:00:00Z", "OFFER@2026-08-27T17:45:00Z"),
                app("Stark", REJECTED, "2026-08-05T14:20:00Z", "REJECTED@2026-08-22T09:30:00Z"));

        AnalyticsView v = AnalyticsService.compute(apps, changes, NOW);

        assertThat(v.total()).isEqualTo(5);
        assertThat(v.active()).isEqualTo(4);
        assertThat(v.countsByStatus()).containsEntry("APPLIED", 1).containsEntry("SCREENING", 1)
                .containsEntry("INTERVIEW", 1).containsEntry("OFFER", 1).containsEntry("REJECTED", 1)
                .containsEntry("ACCEPTED", 0).containsEntry("WITHDRAWN", 0);
        // 4 of 5 left APPLIED; 1 of 5 reached OFFER
        assertThat(v.responseRate()).isEqualTo(0.8);
        assertThat(v.offerRate()).isEqualTo(0.2);
        // first responses took 6.19, 14.11, 26.24, 16.80 days -> median of four = (14.11+16.80)/2
        assertThat(v.medianDaysToFirstResponse()).isCloseTo(15.5, within(0.1));
        // every completed stay was in APPLIED; nobody has left a later stage yet
        assertThat(v.medianDaysInStage()).containsOnlyKeys("APPLIED");
        assertThat(v.medianDaysInStage().get("APPLIED")).isCloseTo(15.5, within(0.1));
    }

    @Test
    void medianUsesTheMiddleValueNotTheAverage() {
        assertThat(AnalyticsService.median(List.of(1.0, 2.0, 200.0))).isEqualTo(2.0);
        assertThat(AnalyticsService.median(List.of(1.0, 3.0))).isEqualTo(2.0);
        assertThat(AnalyticsService.median(List.of(7.0))).isEqualTo(7.0);
    }

    @Test
    void timeInStageOnlyCountsCompletedStays() {
        List<JobApplication> apps = List.of(
                app("A", OFFER, "2026-08-01T00:00:00Z",
                        "SCREENING@2026-08-03T00:00:00Z", "INTERVIEW@2026-08-10T00:00:00Z", "OFFER@2026-08-12T00:00:00Z"),
                app("B", INTERVIEW, "2026-08-01T00:00:00Z",
                        "SCREENING@2026-08-05T00:00:00Z", "INTERVIEW@2026-08-06T00:00:00Z"));

        AnalyticsView v = AnalyticsService.compute(apps, changes, NOW);

        // APPLIED stays: 2 and 4 days -> 3; SCREENING: 7 and 1 -> 4; INTERVIEW: only A completed it (2)
        assertThat(v.medianDaysInStage()).containsEntry("APPLIED", 3.0)
                .containsEntry("SCREENING", 4.0).containsEntry("INTERVIEW", 2.0)
                .doesNotContainKey("OFFER");
    }

    @Test
    void staleMeansWaitingAndQuietForFourteenDaysOrMore() {
        List<JobApplication> apps = List.of(
                app("Quiet", APPLIED, "2026-08-25T12:00:00Z"),            // exactly 14 days: stale
                app("Recent", APPLIED, "2026-08-26T12:00:00Z"),           // 13 days: not yet
                app("Screening", SCREENING, "2026-07-01T00:00:00Z", "SCREENING@2026-08-01T00:00:00Z"), // 38 days quiet
                app("Interviewing", INTERVIEW, "2026-07-01T00:00:00Z", "INTERVIEW@2026-07-02T00:00:00Z"), // never stale
                app("Rejected", REJECTED, "2026-07-01T00:00:00Z", "REJECTED@2026-07-02T00:00:00Z"));

        AnalyticsView v = AnalyticsService.compute(apps, changes, NOW);

        assertThat(v.stale()).extracting(AnalyticsView.StaleApplication::companyName)
                .containsExactly("Screening", "Quiet"); // quietest first
        assertThat(v.stale().get(1).daysSinceChange()).isEqualTo(14);
    }

    @Test
    void weeklyBucketsCoverTwelveWeeksEndingThisWeekWithZerosFilled() {
        List<JobApplication> apps = List.of(
                app("ThisWeek", APPLIED, "2026-09-07T08:00:00Z"),   // Monday of the current week
                app("LastWeek", APPLIED, "2026-09-06T23:00:00Z"),   // Sunday -> previous week
                app("TooOld", APPLIED, "2026-05-01T00:00:00Z"));    // outside the window: ignored

        AnalyticsView v = AnalyticsService.compute(apps, changes, NOW);

        assertThat(v.weeklyApplications()).hasSize(12);
        assertThat(v.weeklyApplications().get(11).weekStart()).isEqualTo(LocalDate.of(2026, 9, 7));
        assertThat(v.weeklyApplications().get(11).created()).isEqualTo(1);
        assertThat(v.weeklyApplications().get(10).weekStart()).isEqualTo(LocalDate.of(2026, 8, 31));
        assertThat(v.weeklyApplications().get(10).created()).isEqualTo(1);
        assertThat(v.weeklyApplications().get(0).weekStart()).isEqualTo(LocalDate.of(2026, 6, 22));
        assertThat(v.weeklyApplications().stream().mapToInt(AnalyticsView.WeekBucket::created).sum()).isEqualTo(2);
    }

    @Test
    void recentActivityIsNewestFirstAndCapped() {
        List<JobApplication> apps = new ArrayList<>();
        for (int i = 0; i < 12; i++) {
            apps.add(app("Co" + i, APPLIED, "2026-08-" + String.format("%02d", i + 1) + "T00:00:00Z"));
        }

        AnalyticsView v = AnalyticsService.compute(apps, changes, NOW);

        assertThat(v.recentActivity()).hasSize(AnalyticsService.RECENT_ACTIVITY);
        assertThat(v.recentActivity().get(0).companyName()).isEqualTo("Co11");
        assertThat(v.recentActivity().get(0).fromStatus()).isNull();
        assertThat(v.recentActivity().get(0).toStatus()).isEqualTo("APPLIED");
        assertThat(v.recentActivity().get(0).source()).isEqualTo(SOURCE_INTAKE);
    }

    @Test
    void forOwnerOnlyEverQueriesThatOwner() {
        JobApplicationRepository apps = mock(JobApplicationRepository.class);
        StatusHistoryRepository history = mock(StatusHistoryRepository.class);
        when(apps.findByOwnerId(7)).thenReturn(List.of());
        when(history.findByApplicationOwnerIdOrderByChangedAtAsc(7)).thenReturn(List.of());
        AnalyticsService service = new AnalyticsService(apps, history, Clock.fixed(NOW, ZoneOffset.UTC));

        AnalyticsView v = service.forOwner(7);

        assertThat(v.total()).isZero();
        verify(apps).findByOwnerId(7);
        verify(history).findByApplicationOwnerIdOrderByChangedAtAsc(7);
    }
}
