package com.resistance.mvc.api;

import com.resistance.shared.models.entity.JobApplication;

import java.time.LocalDate;

/**
 * One tracked application on the wire. Entities are never serialized
 * directly (lazy proxies, the owner link) - the API hands out this flat
 * record instead.
 */
public record ApplicationView(int id, String companyName, String positionTitle,
                              String status, LocalDate appliedOn, String contactName) {

    public static ApplicationView of(JobApplication application) {
        String contactName = application.getContact() == null ? null
                : (application.getContact().getFirstName() + " " + application.getContact().getLastName()).trim();
        return new ApplicationView(
                application.getId(),
                application.getCompanyName(),
                application.getPositionTitle(),
                application.getStatus() == null ? null : application.getStatus().name(),
                application.getAppliedOn(),
                contactName);
    }
}
