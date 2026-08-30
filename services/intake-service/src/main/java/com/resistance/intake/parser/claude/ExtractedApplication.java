package com.resistance.intake.parser.claude;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * The structured-output schema Claude fills in. Field values are treated
 * as untrusted model output and sanitized before use - see
 * ClaudeConfirmationEmailParser#sanitize.
 */
public record ExtractedApplication(
        @JsonPropertyDescription("The employer the user applied to, e.g. 'Acme Corp'. Null if the email is not about a job application.")
        String companyName,
        @JsonPropertyDescription("The job title applied for, e.g. 'Backend Engineer'. Null if not stated.")
        String positionTitle,
        @JsonPropertyDescription("What this email means for the application: APPLIED (confirmation), SCREENING, INTERVIEW (invite), OFFER, REJECTED, ACCEPTED or WITHDRAWN.")
        String status,
        @JsonPropertyDescription("Full name of the human recruiter who sent the original email, or null if it was sent by an automated system.")
        String contactName,
        @JsonPropertyDescription("Email address of that human recruiter, or null.")
        String contactEmail) {
}
