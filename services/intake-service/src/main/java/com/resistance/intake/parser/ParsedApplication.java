package com.resistance.intake.parser;

import com.resistance.shared.models.entity.ApplicationStatus;

/**
 * What the parser understood from one email: which application it is about,
 * what the email means for its status (a confirmation -> APPLIED, a
 * rejection -> REJECTED, an interview invite -> INTERVIEW, an offer ->
 * OFFER), and - when a human recruiter sent it - who they are.
 */
public record ParsedApplication(String companyName,
                                String positionTitle,
                                ApplicationStatus status,
                                String contactName,
                                String contactEmail) {

    public ParsedApplication(String companyName, String positionTitle) {
        this(companyName, positionTitle, ApplicationStatus.APPLIED, null, null);
    }

    public boolean hasContact() {
        return contactEmail != null && !contactEmail.isBlank();
    }
}
