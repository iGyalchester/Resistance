package com.resistance.mvc.assistant;

import com.resistance.mvc.analytics.AnalyticsService;
import com.resistance.mvc.analytics.AnalyticsView;
import com.resistance.mvc.auth.OtpRequestThrottle;
import com.resistance.mvc.dao.UserAccountRepository;
import com.resistance.mvc.service.ContactService;
import com.resistance.mvc.service.JobApplicationService;
import com.resistance.shared.models.entity.Contact;
import com.resistance.shared.models.entity.JobApplication;
import com.resistance.shared.models.entity.UserAccount;
import com.resistance.shared.utils.audit.AuditEventClient;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * One user message in, one streamed reply out. The order matters: the
 * rate limit is checked before any data is read or any token is spent;
 * the prompt is rebuilt from the owner's rows on every message (so it is
 * always current and always scoped); tool calls become proposals for the
 * user and results for the model, at most a few rounds; and the exchange
 * is recorded on the session, audited, and counted.
 */
@Service
public class AssistantService {

    static final int MAX_TOOL_ROUNDS = 3;
    static final String REFUSAL_TEXT = "I can't help with that request.";

    private static final Logger log = LoggerFactory.getLogger(AssistantService.class);

    private final AssistantProperties props;
    private final AssistantModel model;
    private final AssistantTools tools;
    private final AssistantPromptBuilder prompts;
    private final FaqService faq;
    private final JobApplicationService applications;
    private final ContactService contacts;
    private final AnalyticsService analytics;
    private final UserAccountRepository accounts;
    private final OtpRequestThrottle throttle;
    private final AuditEventClient audit;
    private final Counter messages;
    private final Counter inputTokens;
    private final Counter outputTokens;
    private final Counter refusals;
    private final Counter errors;
    private final Counter throttled;

    public AssistantService(AssistantProperties props,
                            AssistantModel assistantModel,
                            AssistantTools assistantTools,
                            AssistantPromptBuilder prompts,
                            FaqService faq,
                            JobApplicationService applications,
                            ContactService contacts,
                            AnalyticsService analytics,
                            UserAccountRepository accounts,
                            OtpRequestThrottle assistantThrottle,
                            AuditEventClient auditEventClient,
                            MeterRegistry meters) {
        this.props = props;
        this.model = assistantModel;
        this.tools = assistantTools;
        this.prompts = prompts;
        this.faq = faq;
        this.applications = applications;
        this.contacts = contacts;
        this.analytics = analytics;
        this.accounts = accounts;
        this.throttle = assistantThrottle;
        this.audit = auditEventClient;
        this.messages = meters.counter("assistant.messages");
        this.inputTokens = meters.counter("assistant.tokens.input");
        this.outputTokens = meters.counter("assistant.tokens.output");
        this.refusals = meters.counter("assistant.refusals");
        this.errors = meters.counter("assistant.errors");
        this.throttled = meters.counter("assistant.throttled");
    }

    public boolean enabled() {
        return props.enabled();
    }

    public void reply(int accountId, Conversation conversation, String message, AssistantListener listener) {
        if (!props.enabled()) {
            listener.onError("assistant_disabled");
            return;
        }
        if (!throttle.tryAcquire("assistant:" + accountId)) {
            throttled.increment();
            listener.onError("rate_limited");
            return;
        }
        Optional<UserAccount> account = accounts.findById(accountId);
        if (account.isEmpty()) {
            listener.onError("unauthenticated");
            return;
        }
        audit.emit("FILE_ACCESS", "ASSISTANT_QUERY", account.get().getEmail(), "assistant", null);
        messages.increment();

        try {
            List<JobApplication> apps = applications.findAllForOwner(accountId);
            List<Contact> people = contacts.findAllForOwner(accountId);
            AnalyticsView summary = analytics.forOwner(accountId);
            String system = prompts.build(apps, people, summary, faq.entries());

            List<ChatMessage> exchange = new ArrayList<>();
            for (Conversation.Turn turn : conversation.turns()) {
                exchange.add(new ChatMessage(turn.role(), List.of(new Part.Text(turn.text()))));
            }
            exchange.add(ChatMessage.user(message));

            StringBuilder spoken = new StringBuilder();
            long in = 0;
            long out = 0;
            for (int round = 0; round <= MAX_TOOL_ROUNDS; round++) {
                List<ToolSpec> specs = round < MAX_TOOL_ROUNDS ? tools.specs() : List.of();
                ModelTurn turn = model.run(system, exchange, specs, listener::onDelta);
                in += turn.inputTokens();
                out += turn.outputTokens();
                spoken.append(turn.text());
                exchange.add(new ChatMessage(ChatMessage.Role.ASSISTANT, turn.parts()));

                if (turn.outcome() == ModelTurn.Outcome.REFUSAL) {
                    refusals.increment();
                    if (turn.text().isBlank()) {
                        listener.onDelta(REFUSAL_TEXT);
                        spoken.append(REFUSAL_TEXT);
                    }
                    break;
                }
                if (turn.outcome() != ModelTurn.Outcome.TOOL_USE) {
                    break;
                }
                List<Part> results = new ArrayList<>();
                for (Part part : turn.parts()) {
                    if (part instanceof Part.ToolUse call) {
                        AssistantTools.Handled handled = tools.handle(call, apps);
                        handled.proposal().ifPresent(listener::onAction);
                        results.add(new Part.ToolResult(call.id(), handled.resultText()));
                    }
                }
                if (results.isEmpty()) {
                    break;
                }
                exchange.add(new ChatMessage(ChatMessage.Role.USER, results));
            }

            conversation.add(ChatMessage.Role.USER, message);
            conversation.add(ChatMessage.Role.ASSISTANT, spoken.toString());
            conversation.trim(props.maxHistoryTurns(), props.maxHistoryChars());
            inputTokens.increment(in);
            outputTokens.increment(out);
            listener.onDone(new AssistantListener.Usage(in, out));
        } catch (ClientGoneException e) {
            // nobody is listening: stop paying for tokens, keep nothing
            log.debug("Assistant reply abandoned, client gone");
        } catch (AssistantUnavailableException e) {
            errors.increment();
            listener.onError("assistant_unavailable");
        } catch (RuntimeException e) {
            log.error("Assistant reply failed", e);
            errors.increment();
            listener.onError("internal");
        }
    }
}
