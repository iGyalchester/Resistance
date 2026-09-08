package com.resistance.mvc.admin;

import com.resistance.shared.models.entity.ApplicationStatus;
import com.resistance.shared.models.entity.JobApplication;
import com.resistance.shared.models.entity.StatusHistory;
import com.resistance.shared.models.entity.UserAccount;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AdminServiceTests {

    private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");
    private static final Instant STARTED = Instant.parse("2026-09-08T06:00:00Z");
    private static final AdminOverview.AssistantStats ASSISTANT = new AdminOverview.AssistantStats(1, 2, 3, 0, 0, 0);
    private static final AdminOverview.AuthStats AUTH = new AdminOverview.AuthStats(5, 1, 3, 2);

    private static UserAccount account(int id, String email, String alias) {
        UserAccount a = new UserAccount("Name " + id, email);
        a.setId(id);
        a.setIntakeAlias(alias);
        return a;
    }

    private static JobApplication app(int id, UserAccount owner, String title, ApplicationStatus status, String updatedAt) {
        JobApplication app = new JobApplication("Co " + id, title, status);
        app.setId(id);
        app.setOwner(owner);
        app.setUpdatedAt(updatedAt == null ? null : Instant.parse(updatedAt));
        return app;
    }

    private static StatusHistory change(String source, String at) {
        return new StatusHistory(null, null, ApplicationStatus.APPLIED, Instant.parse(at), source);
    }

    @Test
    void emptyDeploymentHasZerosNullShareAndThirtyEmptyDays() {
        AdminOverview v = AdminService.overviewOf(List.of(), List.of(), List.of(), ASSISTANT, AUTH, STARTED, NOW);

        assertThat(v.accounts()).isZero();
        assertThat(v.applications()).isZero();
        assertThat(v.applicationsByStatus()).containsKeys("APPLIED", "WITHDRAWN").containsValue(0).hasSize(7);
        assertThat(v.unparsedTitleShare()).isNull();
        assertThat(v.intakeEventsLast30Days()).hasSize(30);
        assertThat(v.intakeEventsLast30Days().getFirst().day()).isEqualTo(LocalDate.parse("2026-08-10"));
        assertThat(v.intakeEventsLast30Days().getLast().day()).isEqualTo(LocalDate.parse("2026-09-08"));
        assertThat(v.manualEventsLast30Days()).allMatch(d -> d.count() == 0);
        assertThat(v.countersSince()).isEqualTo(STARTED);
        assertThat(v.assistant()).isEqualTo(ASSISTANT);
        assertThat(v.auth()).isEqualTo(AUTH);
    }

    @Test
    void countsStatusesUntitledShareAndEventsPerDayBySource() {
        UserAccount boris = account(1, "boris@example.com", "abc");
        List<JobApplication> apps = List.of(
                app(1, boris, "Engineer", ApplicationStatus.APPLIED, "2026-09-01T00:00:00Z"),
                app(2, boris, null, ApplicationStatus.INTERVIEW, "2026-09-02T00:00:00Z"),
                app(3, boris, "  ", ApplicationStatus.INTERVIEW, "2026-09-03T00:00:00Z"),
                app(4, boris, "Dev", null, null));
        List<StatusHistory> recent = List.of(
                change(StatusHistory.SOURCE_INTAKE, "2026-09-08T01:00:00Z"),
                change(StatusHistory.SOURCE_INTAKE, "2026-09-08T23:59:59Z"),
                change(StatusHistory.SOURCE_MANUAL, "2026-09-07T10:00:00Z"),
                change(StatusHistory.SOURCE_INTAKE, "2026-08-10T00:00:00Z"),   // first day of the window
                change(StatusHistory.SOURCE_INTAKE, "2026-08-09T23:59:59Z"),   // just outside: ignored
                change(StatusHistory.SOURCE_MANUAL, "2026-09-09T00:00:00Z"));  // the future: ignored

        AdminOverview v = AdminService.overviewOf(List.of(boris), apps, recent, ASSISTANT, AUTH, STARTED, NOW);

        assertThat(v.applications()).isEqualTo(4);
        assertThat(v.applicationsByStatus()).containsEntry("APPLIED", 2).containsEntry("INTERVIEW", 2).containsEntry("OFFER", 0);
        assertThat(v.unparsedTitleShare()).isEqualTo(0.5);
        assertThat(v.intakeEventsLast30Days().getLast()).isEqualTo(new AdminOverview.DayCount(LocalDate.parse("2026-09-08"), 2));
        assertThat(v.intakeEventsLast30Days().getFirst()).isEqualTo(new AdminOverview.DayCount(LocalDate.parse("2026-08-10"), 1));
        assertThat(v.intakeEventsLast30Days().stream().mapToInt(AdminOverview.DayCount::count).sum()).isEqualTo(3);
        assertThat(v.manualEventsLast30Days().stream().mapToInt(AdminOverview.DayCount::count).sum()).isEqualTo(1);
        assertThat(v.manualEventsLast30Days().get(28)).isEqualTo(new AdminOverview.DayCount(LocalDate.parse("2026-09-07"), 1));
    }

    @Test
    void accountsListCountsPerOwnerNewestActivityFirstAndNeverTheAlias() {
        UserAccount boris = account(1, "boris@example.com", "abc");
        UserAccount quiet = account(2, "quiet@example.com", null);
        UserAccount other = account(3, "aaa@example.com", "zzz");
        List<JobApplication> apps = List.of(
                app(1, boris, "x", ApplicationStatus.APPLIED, "2026-09-01T00:00:00Z"),
                app(2, boris, "y", ApplicationStatus.OFFER, "2026-09-05T00:00:00Z"),
                app(3, other, "z", ApplicationStatus.APPLIED, "2026-09-05T00:00:00Z"),
                app(4, null, "orphan", ApplicationStatus.APPLIED, "2026-09-06T00:00:00Z"));

        List<AdminAccountView> views = AdminService.accountsOf(List.of(boris, quiet, other), apps);

        assertThat(views).extracting(AdminAccountView::email)
                .containsExactly("aaa@example.com", "boris@example.com", "quiet@example.com");
        AdminAccountView b = views.get(1);
        assertThat(b.applicationCount()).isEqualTo(2);
        assertThat(b.lastActivity()).isEqualTo(Instant.parse("2026-09-05T00:00:00Z"));
        assertThat(b.hasAlias()).isTrue();
        assertThat(b.fullName()).isEqualTo("Name 1");
        assertThat(views.get(2).applicationCount()).isZero();
        assertThat(views.get(2).lastActivity()).isNull();
        assertThat(views.get(2).hasAlias()).isFalse();
        assertThat(views.toString()).doesNotContain("abc").doesNotContain("zzz");
    }
}
