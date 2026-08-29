package com.resistance.intake.service;

import com.resistance.shared.models.entity.ApplicationStatus;

/**
 * What one inbound email produced; returned by the webhook so callers
 * (and provider logs) can see whether parsing succeeded.
 */
public record IntakeResult(String outcome,          // CREATED, UPDATED, ALREADY_TRACKED, NOT_PARSED
                           Integer applicationId,
                           String companyName,
                           String positionTitle,
                           ApplicationStatus status,
                           String accountEmail,
                           boolean accountCreated) {

    public static IntakeResult notParsed(String accountEmail, boolean accountCreated) {
        return new IntakeResult("NOT_PARSED", null, null, null, null, accountEmail, accountCreated);
    }
}
