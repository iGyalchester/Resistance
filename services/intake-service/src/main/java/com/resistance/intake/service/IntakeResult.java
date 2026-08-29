package com.resistance.intake.service;

/**
 * What one inbound email produced; returned by the webhook so callers
 * (and provider logs) can see whether parsing succeeded.
 */
public record IntakeResult(String outcome,          // CREATED, ALREADY_TRACKED, NOT_PARSED
                           Integer applicationId,
                           String companyName,
                           String positionTitle,
                           String accountEmail,
                           boolean accountCreated) {

    public static IntakeResult notParsed(String accountEmail, boolean accountCreated) {
        return new IntakeResult("NOT_PARSED", null, null, null, accountEmail, accountCreated);
    }
}
