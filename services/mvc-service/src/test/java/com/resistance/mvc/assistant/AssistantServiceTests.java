package com.resistance.mvc.assistant;

import com.resistance.mvc.analytics.AnalyticsService;
import com.resistance.mvc.analytics.AnalyticsView;
import com.resistance.mvc.auth.OtpRequestThrottle;
import com.resistance.mvc.dao.UserAccountRepository;
import com.resistance.mvc.service.ContactService;
import com.resistance.mvc.service.JobApplicationService;
import com.resistance.shared.models.entity.ApplicationStatus;
import com.resistance.shared.models.entity.JobApplication;
import com.resistance.shared.models.entity.UserAccount;
import com.resistance.shared.utils.audit.AuditEventClient;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AssistantServiceTests {

    private static final int OWNER = 7;

    private final FakeAssistantModel model = new FakeAssistantModel();
    private final JobApplicationService applications = mock(JobApplicationService.class);
    private final ContactService contacts = mock(ContactService.class);
    private final AnalyticsService analytics = mock(AnalyticsService.class);
    private final UserAccountRepository accounts = mock(UserAccountRepository.class);
    private final AuditEventClient audit = mock(AuditEventClient.class);
    private final SimpleMeterRegistry meters = new SimpleMeterRegistry();
    private final Clock clock = Clock.fixed(Instant.parse("2026-09-08T12:00:00Z"), ZoneOffset.UTC);
    private final OtpRequestThrottle throttle = new OtpRequestThrottle(30, Duration.ofHours(1), clock);
    private final Conversation conversation = new Conversation();
    private final RecordingListener listener = new RecordingListener();

    private AssistantService service;
    private JobApplication acme;

    @BeforeEach
    void setUp() {
        acme = new JobApplication("Acme", "Engineer", ApplicationStatus.APPLIED);
        acme.setId(3);
        acme.setUpdatedAt(Instant.parse("2026-08-20T00:00:00Z"));
        when(applications.findAllForOwner(OWNER)).thenReturn(List.of(acme));
        when(contacts.findAllForOwner(OWNER)).thenReturn(List.of());
        when(analytics.forOwner(OWNER)).thenReturn(new AnalyticsView(1, 1, Map.of("APPLIED", 1), null, null, null,
                Map.of(), List.of(), List.of(), List.of()));
        when(accounts.findById(OWNER)).thenReturn(Optional.of(new UserAccount("Boris", "boris@example.com")));
        service = service(props("sk-test", 20, 30_000));
    }

    private AssistantService service(AssistantProperties props) {
        return new AssistantService(props, model, new AssistantTools(),
                new AssistantPromptBuilder(clock), new FaqService(),
                applications, contacts, analytics, accounts, throttle, audit, meters);
    }

    private static AssistantProperties props(String key, int turns, int chars) {
        return new AssistantProperties(key, "claude-opus-5", "medium", 2048, 30, turns, chars);
    }

    @Test
    void streamsTheAnswerStoresTheTurnAuditsAndCounts() {
        model.then(FakeAssistantModel.answer("Acme has been quiet for 19 days."));

        service.reply(OWNER, conversation, "Who should I follow up on?", listener);

        assertThat(listener.events).containsExactly("delta", "delta", "done");
        assertThat(listener.text.toString()).isEqualTo("Acme has been quiet for 19 days.");
        assertThat(listener.usage).isEqualTo(new AssistantListener.Usage(100, 20));

        FakeAssistantModel.Call call = model.calls.getFirst();
        assertThat(call.system()).contains("<user_data>").contains("\"company\":\"Acme\"").contains("<faq>");
        assertThat(call.messages()).hasSize(1);
        assertThat(call.messages().getFirst().role()).isEqualTo(ChatMessage.Role.USER);
        assertThat(call.tools()).hasSize(3);

        assertThat(conversation.turns()).extracting(Conversation.Turn::text)
                .containsExactly("Who should I follow up on?", "Acme has been quiet for 19 days.");
        verify(audit).emit(eq("FILE_ACCESS"), eq("ASSISTANT_QUERY"), eq("boris@example.com"), eq("assistant"), isNull());
        assertThat(meters.counter("assistant.messages").count()).isEqualTo(1);
        assertThat(meters.counter("assistant.tokens.input").count()).isEqualTo(100);
        assertThat(meters.counter("assistant.tokens.output").count()).isEqualTo(20);
    }

    @Test
    void historyIsReplayedAndTrimmed() {
        conversation.add(ChatMessage.Role.USER, "earlier question");
        conversation.add(ChatMessage.Role.ASSISTANT, "earlier answer");
        service = service(props("sk-test", 2, 30_000));
        model.then(FakeAssistantModel.answer("now"));

        service.reply(OWNER, conversation, "follow-up", listener);

        assertThat(model.calls.getFirst().messages()).extracting(m -> ((Part.Text) m.parts().getFirst()).text())
                .containsExactly("earlier question", "earlier answer", "follow-up");
        // max 2 turns: only the newest exchange survives
        assertThat(conversation.turns()).extracting(Conversation.Turn::text).containsExactly("follow-up", "now");
    }

    @Test
    void toolCallBecomesAProposalAndTheResultIsFedBack() {
        model.then(FakeAssistantModel.toolCall("Let me propose that.",
                        new Part.ToolUse("toolu_1", AssistantTools.PROPOSE_STATUS_CHANGE,
                                Map.of("applicationId", 3, "status", "WITHDRAWN", "reason", "Silent for weeks"))))
                .then(FakeAssistantModel.answer(" Confirm the card above."));

        service.reply(OWNER, conversation, "Withdraw Acme", listener);

        assertThat(listener.events).containsExactly("delta", "delta", "action", "delta", "delta", "done");
        assertThat(listener.proposals).hasSize(1);
        assertThat(listener.proposals.getFirst().applicationId()).isEqualTo(3);
        assertThat(listener.proposals.getFirst().status()).isEqualTo("WITHDRAWN");
        assertThat(listener.usage).isEqualTo(new AssistantListener.Usage(200, 40));

        List<ChatMessage> second = model.calls.get(1).messages();
        assertThat(second).hasSize(3);
        assertThat(second.get(1).parts()).anySatisfy(p -> assertThat(p).isInstanceOf(Part.ToolUse.class));
        Part.ToolResult result = (Part.ToolResult) second.get(2).parts().getFirst();
        assertThat(result.toolUseId()).isEqualTo("toolu_1");
        assertThat(result.text()).isEqualTo(AssistantTools.SHOWN_TO_USER);

        // only the spoken text is kept as history
        assertThat(conversation.turns().get(1).text()).isEqualTo("Let me propose that. Confirm the card above.");
        verify(applications, never()).saveForOwner(any(), eq(OWNER));
    }

    @Test
    void foreignApplicationIdYieldsNoProposal() {
        model.then(FakeAssistantModel.toolCall(null,
                        new Part.ToolUse("toolu_1", AssistantTools.PROPOSE_STATUS_CHANGE,
                                Map.of("applicationId", 42, "status", "REJECTED"))))
                .then(FakeAssistantModel.answer("I only see Acme in your tracker."));

        service.reply(OWNER, conversation, "Reject application 42", listener);

        assertThat(listener.proposals).isEmpty();
        Part.ToolResult result = (Part.ToolResult) model.calls.get(1).messages().get(2).parts().getFirst();
        assertThat(result.text()).contains("Unknown application id 42");
        assertThat(listener.events).endsWith("done");
    }

    @Test
    void toolRoundsAreCappedAndTheLastCallOffersNoTools() {
        Part.ToolUse loop = new Part.ToolUse("t", AssistantTools.PROPOSE_CONTACT, Map.of("firstName", "Dana"));
        for (int i = 0; i < AssistantService.MAX_TOOL_ROUNDS; i++) {
            model.then(FakeAssistantModel.toolCall(null, loop));
        }
        model.then(FakeAssistantModel.answer("done proposing"));

        service.reply(OWNER, conversation, "add dana three times", listener);

        assertThat(model.calls).hasSize(AssistantService.MAX_TOOL_ROUNDS + 1);
        assertThat(model.calls.getLast().tools()).isEmpty();
        assertThat(listener.proposals).hasSize(AssistantService.MAX_TOOL_ROUNDS);
        assertThat(listener.events).endsWith("done");
    }

    @Test
    void refusalIsSpokenAndCounted() {
        model.then(FakeAssistantModel.refusal());

        service.reply(OWNER, conversation, "something the model declines", listener);

        assertThat(listener.text.toString()).isEqualTo(AssistantService.REFUSAL_TEXT);
        assertThat(listener.events).containsExactly("delta", "done");
        assertThat(meters.counter("assistant.refusals").count()).isEqualTo(1);
        assertThat(conversation.turns().get(1).text()).isEqualTo(AssistantService.REFUSAL_TEXT);
    }

    @Test
    void providerFailureIsARetryableErrorNotAStackTrace() {
        model.thenThrow(new AssistantUnavailableException("boom", new RuntimeException("secret detail")));

        service.reply(OWNER, conversation, "hi", listener);

        assertThat(listener.events).containsExactly("error");
        assertThat(listener.error).isEqualTo("assistant_unavailable");
        assertThat(conversation.turns()).isEmpty();
        assertThat(meters.counter("assistant.errors").count()).isEqualTo(1);
    }

    @Test
    void unexpectedFailureIsInternal() {
        model.thenThrow(new IllegalStateException("bug"));

        service.reply(OWNER, conversation, "hi", listener);

        assertThat(listener.error).isEqualTo("internal");
        assertThat(meters.counter("assistant.errors").count()).isEqualTo(1);
    }

    @Test
    void thirtyFirstMessageInAnHourIsRateLimitedBeforeAnyDataIsRead() {
        for (int i = 0; i < 30; i++) {
            model.then(FakeAssistantModel.answer("ok"));
            service.reply(OWNER, conversation, "q" + i, new RecordingListener());
        }

        service.reply(OWNER, conversation, "one more", listener);

        assertThat(listener.events).containsExactly("error");
        assertThat(listener.error).isEqualTo("rate_limited");
        assertThat(model.calls).hasSize(30);
        assertThat(meters.counter("assistant.throttled").count()).isEqualTo(1);
        assertThat(meters.counter("assistant.messages").count()).isEqualTo(30);
    }

    @Test
    void anotherAccountHasItsOwnBudget() {
        for (int i = 0; i < 30; i++) {
            model.then(FakeAssistantModel.answer("ok"));
            service.reply(OWNER, conversation, "q" + i, new RecordingListener());
        }
        when(applications.findAllForOwner(8)).thenReturn(List.of());
        when(contacts.findAllForOwner(8)).thenReturn(List.of());
        when(analytics.forOwner(8)).thenReturn(new AnalyticsView(0, 0, Map.of(), null, null, null,
                Map.of(), List.of(), List.of(), List.of()));
        when(accounts.findById(8)).thenReturn(Optional.of(new UserAccount("Other", "other@example.com")));
        model.then(FakeAssistantModel.answer("hello other"));

        service.reply(8, new Conversation(), "hi", listener);

        assertThat(listener.error).isNull();
        assertThat(listener.text.toString()).isEqualTo("hello other");
        assertThat(model.calls.getLast().system()).doesNotContain("Acme");
    }

    @Test
    void disabledAssistantAnswersWithTheDisabledCodeAndTouchesNothing() {
        service = service(props("", 20, 30_000));

        service.reply(OWNER, conversation, "hi", listener);

        assertThat(listener.error).isEqualTo("assistant_disabled");
        assertThat(model.calls).isEmpty();
        verify(audit, never()).emit(any(), any(), any(), any(), any());
    }
}
