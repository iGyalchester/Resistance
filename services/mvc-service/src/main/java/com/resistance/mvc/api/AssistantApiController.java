package com.resistance.mvc.api;

import com.resistance.mvc.assistant.AssistantDisabledException;
import com.resistance.mvc.assistant.AssistantListener;
import com.resistance.mvc.assistant.AssistantService;
import com.resistance.mvc.assistant.Conversation;
import com.resistance.mvc.assistant.Proposal;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import com.fasterxml.jackson.annotation.JsonInclude;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * The chat endpoint. A message goes in as JSON; the reply comes back as a
 * server-sent event stream so words appear as the model produces them:
 * {@code delta {text}} fragments, {@code action {proposal}} cards,
 * then one {@code done {usage}} or one {@code error {code}}. The
 * conversation lives on the HTTP session and is dropped on logout or on
 * DELETE.
 */
@RestController
@RequestMapping("/api/assistant")
public class AssistantApiController {

    static final String SESSION_CONVERSATION = "assistantConversation";
    static final long TIMEOUT_MILLIS = 120_000;

    private static final Logger log = LoggerFactory.getLogger(AssistantApiController.class);

    private final AssistantService assistant;
    private final Executor executor;
    // nulls omitted, matching the rest of the API's JSON
    private final JsonMapper json = JsonMapper.builder()
            .changeDefaultPropertyInclusion(incl -> incl.withValueInclusion(JsonInclude.Include.NON_NULL))
            .build();

    /**
     * Replies stream for seconds, so they run off the servlet thread on
     * Boot's application executor (virtual threads, see
     * spring.threads.virtual.enabled); the request thread is free the
     * moment the emitter is handed back.
     */
    public AssistantApiController(AssistantService assistant,
                                  @Qualifier("applicationTaskExecutor") Executor executor) {
        this.assistant = assistant;
        this.executor = executor;
    }

    @PostMapping(value = "/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public ResponseEntity<SseEmitter> send(@Valid @RequestBody AssistantMessageRequest body, HttpSession session) {
        Integer accountId = ApiSessions.accountId(session);
        if (accountId == null) {
            throw new UnauthenticatedException();
        }
        if (!assistant.enabled()) {
            throw new AssistantDisabledException();
        }
        Conversation conversation = conversation(session);
        SseEmitter emitter = new SseEmitter(TIMEOUT_MILLIS);
        emitter.onTimeout(emitter::complete);
        executor.execute(() -> assistant.reply(accountId, conversation, body.message().trim(), new Events(emitter)));
        return ResponseEntity.ok().contentType(MediaType.TEXT_EVENT_STREAM).body(emitter);
    }

    @DeleteMapping("/conversation")
    public ResponseEntity<Object> reset(HttpSession session) {
        Integer accountId = ApiSessions.accountId(session);
        if (accountId == null) {
            return ApiSessions.unauthorized();
        }
        session.removeAttribute(SESSION_CONVERSATION);
        return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
    }

    private static Conversation conversation(HttpSession session) {
        synchronized (session) {
            Object existing = session.getAttribute(SESSION_CONVERSATION);
            if (existing instanceof Conversation conversation) {
                return conversation;
            }
            Conversation fresh = new Conversation();
            session.setAttribute(SESSION_CONVERSATION, fresh);
            return fresh;
        }
    }

    /** Listener → SSE. Every payload is serialized here so the wire shape is one place. */
    private final class Events implements AssistantListener {

        private final SseEmitter emitter;

        Events(SseEmitter emitter) {
            this.emitter = emitter;
        }

        @Override
        public void onDelta(String text) {
            send("delta", Map.of("text", text));
        }

        @Override
        public void onAction(Proposal proposal) {
            send("action", Map.of("proposal", proposal));
        }

        @Override
        public void onDone(Usage usage) {
            send("done", Map.of("usage", usage));
            emitter.complete();
        }

        @Override
        public void onError(String code) {
            send("error", Map.of("code", code));
            emitter.complete();
        }

        private void send(String name, Object payload) {
            try {
                emitter.send(SseEmitter.event().name(name).data(json.writeValueAsString(payload), MediaType.APPLICATION_JSON));
            } catch (IOException | IllegalStateException e) {
                // the browser went away mid-answer; nothing to do but stop
                log.debug("SSE send failed ({}), client gone", e.getMessage());
                emitter.completeWithError(e);
            }
        }
    }
}
