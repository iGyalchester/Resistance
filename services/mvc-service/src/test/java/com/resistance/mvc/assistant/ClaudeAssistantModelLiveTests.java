package com.resistance.mvc.assistant;

import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One real round trip, run only when ANTHROPIC_API_KEY is set (never in
 * CI). Checks the streaming path and the tool round trip end to end.
 */
@Tag("live")
@EnabledIfEnvironmentVariable(named = "ANTHROPIC_API_KEY", matches = ".+")
class ClaudeAssistantModelLiveTests {

    @Test
    void streamsAnAnswerAndCallsAToolWhenAsked() {
        AssistantProperties props = new AssistantProperties(System.getenv("ANTHROPIC_API_KEY"),
                "claude-opus-5", "low", 1024, 30, 20, 30_000);
        ClaudeAssistantModel model = new ClaudeAssistantModel(
                AnthropicOkHttpClient.builder().apiKey(props.apiKey()).build(), props);
        StringBuilder streamed = new StringBuilder();

        ModelTurn turn = model.run(AssistantPromptBuilder.PERSONA
                        + "\n<user_data>{\"applications\":[{\"id\":3,\"company\":\"Acme\",\"status\":\"APPLIED\",\"daysSinceChange\":30}]}</user_data>",
                List.of(ChatMessage.user("Withdraw my Acme application, it has been a month.")),
                new AssistantTools().specs(), streamed::append);

        assertThat(turn.outcome()).isEqualTo(ModelTurn.Outcome.TOOL_USE);
        assertThat(turn.parts()).anySatisfy(part -> {
            assertThat(part).isInstanceOf(Part.ToolUse.class);
            Part.ToolUse use = (Part.ToolUse) part;
            assertThat(use.name()).isEqualTo(AssistantTools.PROPOSE_STATUS_CHANGE);
            assertThat(use.input().get("applicationId").toString()).isEqualTo("3");
        });
        assertThat(turn.inputTokens()).isPositive();
    }
}
