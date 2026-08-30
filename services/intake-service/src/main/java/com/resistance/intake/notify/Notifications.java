package com.resistance.intake.notify;

import com.resistance.shared.models.entity.ApplicationStatus;
import com.resistance.shared.models.entity.JobApplication;
import com.resistance.shared.models.entity.UserAccount;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Pure message composition, kept free of mail plumbing so it can be
 * unit-tested and reused by any delivery channel.
 */
public final class Notifications {

    private Notifications() {
    }

    public static String changeSubject(JobApplication application, ApplicationStatus fromStatus) {
        if (fromStatus == null) {
            return "Now tracking: " + application.getCompanyName();
        }
        return application.getCompanyName() + " moved to " + application.getStatus();
    }

    public static String changeBody(UserAccount account, JobApplication application,
                                    ApplicationStatus fromStatus) {
        StringBuilder body = new StringBuilder("Hi ").append(account.getFullName()).append(",\n\n");

        if (fromStatus == null) {
            body.append("Your application to ").append(application.getCompanyName());
            if (application.getPositionTitle() != null) {
                body.append(" (").append(application.getPositionTitle()).append(")");
            }
            body.append(" is now tracked with status ").append(application.getStatus()).append(".\n");
        } else {
            body.append(application.getCompanyName());
            if (application.getPositionTitle() != null) {
                body.append(" - ").append(application.getPositionTitle());
            }
            body.append(" changed from ").append(fromStatus)
                    .append(" to ").append(application.getStatus()).append(".\n");
        }

        body.append("\nLog in to your dashboard for details.\n");
        return body.toString();
    }

    /** One account's weekly summary: counts per status plus the roster. */
    public static String digestBody(UserAccount account, List<JobApplication> applications) {
        Map<ApplicationStatus, Integer> counts = new EnumMap<>(ApplicationStatus.class);
        for (JobApplication application : applications) {
            if (application.getStatus() != null) {
                counts.merge(application.getStatus(), 1, Integer::sum);
            }
        }

        StringBuilder body = new StringBuilder("Hi ").append(account.getFullName()).append(",\n\n")
                .append("Your week in job applications (").append(applications.size())
                .append(" tracked):\n\n");

        counts.forEach((status, count) ->
                body.append("  ").append(status).append(": ").append(count).append('\n'));

        body.append('\n');
        for (JobApplication application : applications) {
            body.append("  - ").append(application.getCompanyName());
            if (application.getPositionTitle() != null) {
                body.append(" (").append(application.getPositionTitle()).append(")");
            }
            body.append(": ").append(application.getStatus()).append('\n');
        }

        body.append("\nKeep going!\n");
        return body.toString();
    }
}
