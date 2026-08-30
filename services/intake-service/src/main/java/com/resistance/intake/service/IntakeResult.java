package com.resistance.intake.service;

import com.resistance.shared.models.entity.ApplicationStatus;

/**
 * What one inbound email produced; returned by the webhook so callers
 * (and provider logs) can see whether parsing succeeded. intakeAlias is
 * echoed so a first-time forwarder can learn their personal intake
 * address (track+<alias>@domain).
 */
public record IntakeResult(String outcome,          // CREATED, UPDATED, ALREADY_TRACKED, NOT_PARSED,
                                                    // IGNORED_UNKNOWN_ALIAS, IGNORED_NO_ALIAS
                           Integer applicationId,
                           String companyName,
                           String positionTitle,
                           ApplicationStatus status,
                           String accountEmail,
                           boolean accountCreated,
                           String intakeAlias) {

    public static IntakeResult notParsed(String accountEmail, boolean accountCreated, String intakeAlias) {
        return new IntakeResult("NOT_PARSED", null, null, null, null, accountEmail, accountCreated, intakeAlias);
    }

    public static IntakeResult ignored(String outcome) {
        return new IntakeResult(outcome, null, null, null, null, null, false, null);
    }
}
