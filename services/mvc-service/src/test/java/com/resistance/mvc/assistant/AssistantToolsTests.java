package com.resistance.mvc.assistant;

import com.resistance.shared.models.entity.ApplicationStatus;
import com.resistance.shared.models.entity.JobApplication;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class AssistantToolsTests {

    private final AssistantTools tools = new AssistantTools();
    private final JobApplication acme = app(3, "Acme", ApplicationStatus.APPLIED);
    private final List<JobApplication> mine = List.of(acme, app(4, "Globex", ApplicationStatus.INTERVIEW));

    private static JobApplication app(int id, String company, ApplicationStatus status) {
        JobApplication app = new JobApplication(company, null, status);
        app.setId(id);
        return app;
    }

    private static Part.ToolUse call(String name, Map<String, Object> input) {
        return new Part.ToolUse("toolu_1", name, input);
    }

    @Test
    void threeToolsAreOffered() {
        assertThat(tools.specs()).extracting(ToolSpec::name).containsExactly(
                AssistantTools.PROPOSE_STATUS_CHANGE, AssistantTools.PROPOSE_NEW_APPLICATION, AssistantTools.PROPOSE_CONTACT);
        assertThat(tools.specs().getFirst().required()).containsExactly("applicationId", "status");
    }

    @Test
    void statusChangeForOwnApplicationBecomesAProposal() {
        AssistantTools.Handled handled = tools.handle(call(AssistantTools.PROPOSE_STATUS_CHANGE,
                Map.of("applicationId", 3, "status", "withdrawn", "reason", "No reply in 3 weeks")), mine);

        assertThat(handled.proposal()).isPresent();
        Proposal p = handled.proposal().get();
        assertThat(p.kind()).isEqualTo("status_change");
        assertThat(p.applicationId()).isEqualTo(3);
        assertThat(p.companyName()).isEqualTo("Acme");
        assertThat(p.status()).isEqualTo("WITHDRAWN");
        assertThat(p.reason()).isEqualTo("No reply in 3 weeks");
        assertThat(handled.resultText()).isEqualTo(AssistantTools.SHOWN_TO_USER);
    }

    @Test
    void foreignOrUnknownApplicationIdIsRejectedWithoutAProposal() {
        AssistantTools.Handled handled = tools.handle(call(AssistantTools.PROPOSE_STATUS_CHANGE,
                Map.of("applicationId", 99, "status", "REJECTED")), mine);

        assertThat(handled.proposal()).isEmpty();
        assertThat(handled.resultText()).contains("Unknown application id 99");
    }

    @Test
    void junkStatusAndJunkIdAreRejected() {
        assertThat(tools.handle(call(AssistantTools.PROPOSE_STATUS_CHANGE,
                Map.of("applicationId", 3, "status", "GHOSTED")), mine).resultText()).contains("Unknown status");
        assertThat(tools.handle(call(AssistantTools.PROPOSE_STATUS_CHANGE,
                Map.of("applicationId", "three", "status", "REJECTED")), mine).resultText()).contains("must be an integer");
        assertThat(tools.handle(call(AssistantTools.PROPOSE_STATUS_CHANGE,
                Map.of("applicationId", 3, "status", "APPLIED")), mine).resultText()).contains("already APPLIED");
    }

    @Test
    void stringIdsFromTheModelAreAccepted() {
        AssistantTools.Handled handled = tools.handle(call(AssistantTools.PROPOSE_STATUS_CHANGE,
                Map.of("applicationId", "4", "status", "OFFER")), mine);
        assertThat(handled.proposal()).isPresent();
        assertThat(handled.proposal().get().companyName()).isEqualTo("Globex");
    }

    @Test
    void newApplicationDefaultsToAppliedAndCapsFields() {
        Map<String, Object> input = new HashMap<>();
        input.put("companyName", "  " + "x".repeat(200) + "  ");
        AssistantTools.Handled handled = tools.handle(call(AssistantTools.PROPOSE_NEW_APPLICATION, input), mine);

        assertThat(handled.proposal()).isPresent();
        assertThat(handled.proposal().get().kind()).isEqualTo("new_application");
        assertThat(handled.proposal().get().companyName()).hasSize(AssistantTools.MAX_FIELD);
        assertThat(handled.proposal().get().status()).isEqualTo("APPLIED");
        assertThat(handled.proposal().get().positionTitle()).isNull();

        assertThat(tools.handle(call(AssistantTools.PROPOSE_NEW_APPLICATION, Map.of("positionTitle", "Dev")), mine)
                .resultText()).contains("companyName is required");
    }

    @Test
    void contactNeedsAFirstNameAndAValidEmail() {
        assertThat(tools.handle(call(AssistantTools.PROPOSE_CONTACT, Map.of("lastName", "Lee")), mine)
                .resultText()).contains("firstName is required");
        assertThat(tools.handle(call(AssistantTools.PROPOSE_CONTACT, Map.of("firstName", "Dana", "email", "not-an-email")), mine)
                .resultText()).contains("not a valid address");

        AssistantTools.Handled ok = tools.handle(call(AssistantTools.PROPOSE_CONTACT,
                Map.of("firstName", "Dana", "lastName", "Lee", "email", "dana@acme.example")), mine);
        assertThat(ok.proposal()).isPresent();
        assertThat(ok.proposal().get().kind()).isEqualTo("contact");
        assertThat(ok.proposal().get().email()).isEqualTo("dana@acme.example");
    }

    @Test
    void unknownToolNameIsAnsweredNotThrown() {
        AssistantTools.Handled handled = tools.handle(call("delete_everything", Map.of()), mine);
        assertThat(handled.proposal()).isEmpty();
        assertThat(handled.resultText()).contains("Unknown tool");
    }
}
