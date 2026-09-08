package com.resistance.mvc.assistant;

import com.anthropic.core.ObjectMappers;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.Tool;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/** The pure mapping between our port types and the SDK's, no network. */
class ClaudeAssistantModelTests {

    /** Builds a Message the way the SDK does: from the API's JSON. */
    private static Message message(String stop, String... contentJson) {
        String json = "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\",\"model\":\"claude-opus-5\","
                + "\"content\":[" + String.join(",", contentJson) + "],"
                + "\"stop_reason\":\"" + stop + "\",\"stop_sequence\":null,"
                + "\"usage\":{\"input_tokens\":12,\"output_tokens\":3}}";
        try {
            return ObjectMappers.jsonMapper().readValue(json, Message.class);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Test
    void textAndToolUseBlocksBecomeParts() {
        Message m = message("tool_use",
                "{\"type\":\"text\",\"text\":\"Sure.\"}",
                "{\"type\":\"tool_use\",\"id\":\"toolu_9\",\"name\":\"propose_contact\","
                        + "\"input\":{\"firstName\":\"Dana\",\"count\":2}}");

        ModelTurn turn = ClaudeAssistantModel.toTurn(m);

        assertThat(turn.outcome()).isEqualTo(ModelTurn.Outcome.TOOL_USE);
        assertThat(turn.inputTokens()).isEqualTo(12);
        assertThat(turn.outputTokens()).isEqualTo(3);
        assertThat(turn.text()).isEqualTo("Sure.");
        Part.ToolUse use = (Part.ToolUse) turn.parts().get(1);
        assertThat(use.id()).isEqualTo("toolu_9");
        assertThat(use.name()).isEqualTo("propose_contact");
        assertThat(use.input()).containsEntry("firstName", "Dana").containsEntry("count", 2);
    }

    @Test
    void stopReasonsMapToOutcomes() {
        assertThat(ClaudeAssistantModel.toTurn(message("end_turn")).outcome()).isEqualTo(ModelTurn.Outcome.END);
        assertThat(ClaudeAssistantModel.toTurn(message("refusal")).outcome()).isEqualTo(ModelTurn.Outcome.REFUSAL);
        assertThat(ClaudeAssistantModel.toTurn(message("max_tokens")).outcome()).isEqualTo(ModelTurn.Outcome.MAX_TOKENS);
    }

    @Test
    void toolSpecBecomesAnApiToolWithSchema() {
        Tool tool = ClaudeAssistantModel.toTool(new AssistantTools().specs().getFirst());

        assertThat(tool.name()).isEqualTo(AssistantTools.PROPOSE_STATUS_CHANGE);
        assertThat(tool.description()).isPresent();
        assertThat(tool.inputSchema().required()).contains(List.of("applicationId", "status"));
        assertThat(tool.inputSchema()._properties().toString()).contains("applicationId");
    }

    @Test
    void messagesRoundTripIncludingToolResults() {
        MessageParam assistant = ClaudeAssistantModel.toParam(new ChatMessage(ChatMessage.Role.ASSISTANT, List.of(
                new Part.Text(""), new Part.Text("Proposing."),
                new Part.ToolUse("toolu_1", "propose_contact", Map.of("firstName", "Dana")))));
        MessageParam user = ClaudeAssistantModel.toParam(new ChatMessage(ChatMessage.Role.USER, List.of(
                new Part.ToolResult("toolu_1", "shown"))));

        assertThat(assistant.role()).isEqualTo(MessageParam.Role.ASSISTANT);
        List<?> blocks = assistant.content().blockParams().orElseThrow();
        assertThat(blocks).hasSize(2); // the empty text block is dropped
        assertThat(user.content().blockParams().orElseThrow().getFirst().toolResult()).isPresent();
        assertThat(user.content().blockParams().orElseThrow().getFirst().toolResult().get().toolUseId()).isEqualTo("toolu_1");
    }
}
