package com.resistance.mvc.assistant;

/**
 * A change the assistant suggests. It is only ever shown to the user; the
 * UI applies it through the ordinary REST endpoints once they confirm, so
 * the model has no write path of its own. Unused fields are null and
 * omitted from JSON.
 *
 * @param kind one of status_change, new_application, contact
 */
public record Proposal(String kind,
                       Integer applicationId,
                       String companyName,
                       String positionTitle,
                       String status,
                       String reason,
                       String firstName,
                       String lastName,
                       String email) {

    public static Proposal statusChange(int applicationId, String companyName, String status, String reason) {
        return new Proposal("status_change", applicationId, companyName, null, status, reason, null, null, null);
    }

    public static Proposal newApplication(String companyName, String positionTitle, String status) {
        return new Proposal("new_application", null, companyName, positionTitle, status, null, null, null, null);
    }

    public static Proposal contact(String firstName, String lastName, String email) {
        return new Proposal("contact", null, null, null, null, null, firstName, lastName, email);
    }
}
