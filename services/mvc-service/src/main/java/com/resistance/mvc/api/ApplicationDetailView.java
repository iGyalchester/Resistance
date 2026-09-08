package com.resistance.mvc.api;

import com.resistance.shared.models.entity.JobApplication;
import com.resistance.shared.models.entity.StatusHistory;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/** ApplicationView plus what the detail page needs: the contact id for editing and the timeline. */
public record ApplicationDetailView(int id, String companyName, String positionTitle, String status,
                                    LocalDate appliedOn, Instant updatedAt,
                                    Integer contactId, String contactName,
                                    List<StatusChangeView> history) {

    public static ApplicationDetailView of(JobApplication application, List<StatusHistory> history) {
        ApplicationView flat = ApplicationView.of(application);
        return new ApplicationDetailView(
                flat.id(), flat.companyName(), flat.positionTitle(), flat.status(), flat.appliedOn(),
                application.getUpdatedAt(),
                flat.contactId(),
                flat.contactName(),
                history.stream().map(StatusChangeView::of).toList());
    }
}
