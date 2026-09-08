package com.resistance.mvc.api;

import com.resistance.mvc.assistant.AssistantListener;
import com.resistance.mvc.assistant.AssistantService;
import com.resistance.mvc.assistant.Conversation;
import com.resistance.mvc.assistant.Proposal;
import com.resistance.mvc.auth.SessionAuthenticator;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AssistantApiControllerTests {

    private final AssistantService assistant = mock(AssistantService.class);
    // the reply runs inline so the whole stream is written before perform() returns
    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new AssistantApiController(assistant, Runnable::run))
            .setControllerAdvice(new ApiErrorHandler())
            .build();

    private MockHttpSession loggedIn() {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute(SessionAuthenticator.SESSION_ACCOUNT_ID, 7);
        return session;
    }

    private void script(java.util.function.Consumer<AssistantListener> reply) {
        when(assistant.enabled()).thenReturn(true);
        doAnswer(invocation -> {
            reply.accept(invocation.getArgument(3));
            return null;
        }).when(assistant).reply(eq(7), any(Conversation.class), any(), any());
    }

    @Test
    void streamsDeltaAndDoneEvents() throws Exception {
        script(l -> {
            l.onDelta("Hello, ");
            l.onDelta("Boris.");
            l.onDone(new AssistantListener.Usage(10, 2));
        });

        MvcResult result = mockMvc.perform(post("/api/assistant/messages").session(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"  hi  \"}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM))
                .andReturn();

        String body = result.getResponse().getContentAsString();
        assertThat(body).contains("event:delta\ndata:{\"text\":\"Hello, \"}")
                .contains("event:delta\ndata:{\"text\":\"Boris.\"}")
                .contains("event:done\ndata:{\"usage\":{\"inputTokens\":10,\"outputTokens\":2}}");
        verify(assistant).reply(eq(7), any(Conversation.class), eq("hi"), any());
    }

    @Test
    void proposalsBecomeActionEventsAndErrorsBecomeErrorEvents() throws Exception {
        script(l -> {
            l.onAction(Proposal.statusChange(3, "Acme", "WITHDRAWN", "quiet"));
            l.onError("rate_limited");
        });

        String body = mockMvc.perform(post("/api/assistant/messages").session(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"withdraw acme\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertThat(body).contains("event:action\ndata:{\"proposal\":{\"kind\":\"status_change\",\"applicationId\":3,"
                + "\"companyName\":\"Acme\",\"status\":\"WITHDRAWN\",\"reason\":\"quiet\"}}");
        assertThat(body).contains("event:error\ndata:{\"code\":\"rate_limited\"}");
    }

    @Test
    void conversationIsKeptOnTheSessionAcrossMessages() throws Exception {
        script(l -> l.onDone(new AssistantListener.Usage(1, 1)));
        MockHttpSession session = loggedIn();

        mockMvc.perform(post("/api/assistant/messages").session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"one\"}")).andExpect(status().isOk());
        mockMvc.perform(post("/api/assistant/messages").session(session)
                .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"two\"}")).andExpect(status().isOk());

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(assistant, org.mockito.Mockito.times(2)).reply(eq(7), captor.capture(), any(), any());
        assertThat(captor.getAllValues().get(0)).isSameAs(captor.getAllValues().get(1));
        assertThat(session.getAttribute(AssistantApiController.SESSION_CONVERSATION)).isSameAs(captor.getValue());
    }

    @Test
    void resetDropsTheConversation() throws Exception {
        MockHttpSession session = loggedIn();
        session.setAttribute(AssistantApiController.SESSION_CONVERSATION, new Conversation());

        mockMvc.perform(delete("/api/assistant/conversation").session(session)).andExpect(status().isNoContent());

        assertThat(session.getAttribute(AssistantApiController.SESSION_CONVERSATION)).isNull();
    }

    @Test
    void anonymousIs401() throws Exception {
        when(assistant.enabled()).thenReturn(true);

        mockMvc.perform(post("/api/assistant/messages")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("unauthenticated"));
        mockMvc.perform(delete("/api/assistant/conversation")).andExpect(status().isUnauthorized());

        verify(assistant, never()).reply(anyInt(), any(), any(), any());
    }

    @Test
    void disabledAssistantIs503() throws Exception {
        when(assistant.enabled()).thenReturn(false);

        mockMvc.perform(post("/api/assistant/messages").session(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"hi\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error").value("assistant_disabled"));

        verify(assistant, never()).reply(anyInt(), any(), any(), any());
    }

    @Test
    void blankOrOversizedMessageIs400() throws Exception {
        when(assistant.enabled()).thenReturn(true);

        mockMvc.perform(post("/api/assistant/messages").session(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("validation"))
                .andExpect(jsonPath("$.fields.message").exists());
        mockMvc.perform(post("/api/assistant/messages").session(loggedIn())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"message\":\"" + "x".repeat(4001) + "\"}"))
                .andExpect(status().isBadRequest());

        verify(assistant, never()).reply(anyInt(), any(), any(), any());
    }
}
