package com.resistance.mvc.assistant;

import com.resistance.shared.models.entity.ApplicationStatus;
import com.resistance.shared.models.entity.JobApplication;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * The three things the assistant can suggest, and the checks every
 * suggestion passes before the user sees it. None of these write: a
 * "tool call" produces a {@link Proposal} for the UI and the model is told
 * the user must confirm. Ids are validated against the caller's own
 * applications, so a hallucinated or foreign id becomes "unknown", not a
 * card.
 */
public class AssistantTools {

    public static final String PROPOSE_STATUS_CHANGE = "propose_status_change";
    public static final String PROPOSE_NEW_APPLICATION = "propose_new_application";
    public static final String PROPOSE_CONTACT = "propose_contact";

    static final String SHOWN_TO_USER = "Proposal shown to the user; they must confirm it in the UI before anything changes. "
            + "Do not claim the change has been made.";
    static final int MAX_FIELD = 90;

    private static final Pattern EMAIL = Pattern.compile("^[\\w.%+-]+@[\\w.-]+\\.[A-Za-z]{2,}$");
    private static final String STATUS_LIST = String.join(", ",
            Arrays.stream(ApplicationStatus.values()).map(Enum::name).toList());

    /** The result of handling one tool call: maybe a proposal, always a message for the model. */
    public record Handled(Optional<Proposal> proposal, String resultText) {
    }

    public List<ToolSpec> specs() {
        return List.of(
                new ToolSpec(PROPOSE_STATUS_CHANGE,
                        "Propose moving one of the user's applications to a new status. Use only ids from user_data. "
                                + "The user confirms in the UI; never report the change as done.",
                        Map.of(
                                "applicationId", Map.of("type", "integer", "description", "id from user_data.applications"),
                                "status", Map.of("type", "string", "enum", Arrays.stream(ApplicationStatus.values()).map(Enum::name).toList(),
                                        "description", "the new status"),
                                "reason", Map.of("type", "string", "description", "one short sentence on why, shown to the user")),
                        List.of("applicationId", "status")),
                new ToolSpec(PROPOSE_NEW_APPLICATION,
                        "Propose tracking a new job application the user mentions. The user confirms in the UI.",
                        Map.of(
                                "companyName", Map.of("type", "string", "description", "the company"),
                                "positionTitle", Map.of("type", "string", "description", "the role, if known"),
                                "status", Map.of("type", "string", "enum", Arrays.stream(ApplicationStatus.values()).map(Enum::name).toList(),
                                        "description", "starting status, usually APPLIED")),
                        List.of("companyName")),
                new ToolSpec(PROPOSE_CONTACT,
                        "Propose adding a recruiter or hiring contact to the user's address book. The user confirms in the UI.",
                        Map.of(
                                "firstName", Map.of("type", "string"),
                                "lastName", Map.of("type", "string"),
                                "email", Map.of("type", "string", "description", "their email address, if known")),
                        List.of("firstName")));
    }

    /**
     * Validates one call against the caller's applications. Anything the
     * model got wrong comes back as a plain message it can correct from;
     * nothing here throws.
     */
    public Handled handle(Part.ToolUse call, List<JobApplication> ownersApplications) {
        Map<String, Object> in = call.input() == null ? Map.of() : call.input();
        return switch (call.name()) {
            case PROPOSE_STATUS_CHANGE -> statusChange(in, ownersApplications);
            case PROPOSE_NEW_APPLICATION -> newApplication(in);
            case PROPOSE_CONTACT -> contact(in);
            default -> new Handled(Optional.empty(), "Unknown tool " + call.name() + ".");
        };
    }

    private Handled statusChange(Map<String, Object> in, List<JobApplication> apps) {
        Integer id = integer(in.get("applicationId"));
        if (id == null) {
            return new Handled(Optional.empty(), "applicationId must be an integer id from user_data.");
        }
        Optional<JobApplication> app = apps.stream().filter(a -> a.getId() == id).findFirst();
        if (app.isEmpty()) {
            return new Handled(Optional.empty(), "Unknown application id " + id + "; only ids listed in user_data are valid.");
        }
        Optional<ApplicationStatus> status = ApplicationStatus.fromString(string(in.get("status")));
        if (status.isEmpty()) {
            return new Handled(Optional.empty(), "Unknown status; valid values are " + STATUS_LIST + ".");
        }
        if (app.get().getStatus() == status.get()) {
            return new Handled(Optional.empty(), "The application is already " + status.get().name() + "; no change needed.");
        }
        return new Handled(Optional.of(Proposal.statusChange(id, app.get().getCompanyName(),
                status.get().name(), cap(string(in.get("reason"))))), SHOWN_TO_USER);
    }

    private Handled newApplication(Map<String, Object> in) {
        String company = cap(string(in.get("companyName")));
        if (company == null || company.isBlank()) {
            return new Handled(Optional.empty(), "companyName is required.");
        }
        ApplicationStatus status = ApplicationStatus.fromString(string(in.get("status"))).orElse(ApplicationStatus.APPLIED);
        return new Handled(Optional.of(Proposal.newApplication(company, cap(string(in.get("positionTitle"))), status.name())),
                SHOWN_TO_USER);
    }

    private Handled contact(Map<String, Object> in) {
        String first = cap(string(in.get("firstName")));
        if (first == null || first.isBlank()) {
            return new Handled(Optional.empty(), "firstName is required.");
        }
        String email = cap(string(in.get("email")));
        if (email != null && !EMAIL.matcher(email).matches()) {
            return new Handled(Optional.empty(), "email is not a valid address; omit it or correct it.");
        }
        return new Handled(Optional.of(Proposal.contact(first, cap(string(in.get("lastName"))), email)), SHOWN_TO_USER);
    }

    private static Integer integer(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        if (value instanceof String s) {
            try {
                return Integer.parseInt(s.trim());
            } catch (NumberFormatException e) {
                return null;
            }
        }
        return null;
    }

    private static String string(Object value) {
        return value == null ? null : value.toString();
    }

    private static String cap(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        return trimmed.length() > MAX_FIELD ? trimmed.substring(0, MAX_FIELD) : trimmed;
    }
}
