package com.resistance.mvc.assistant;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.core.http.StreamResponse;
import com.anthropic.errors.AnthropicException;
import com.anthropic.helpers.MessageAccumulator;
import com.anthropic.models.messages.ContentBlock;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.Message;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.MessageParam;
import com.anthropic.models.messages.OutputConfig;
import com.anthropic.models.messages.RawMessageStreamEvent;
import com.anthropic.models.messages.TextBlockParam;
import com.anthropic.models.messages.Tool;
import com.anthropic.models.messages.ToolChoiceAuto;
import com.anthropic.models.messages.ToolResultBlockParam;
import com.anthropic.models.messages.ToolUseBlockParam;
import com.fasterxml.jackson.core.type.TypeReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * The Anthropic Messages API behind {@link AssistantModel}. Streams so the
 * first words reach the browser in under a second instead of after the
 * whole answer; the accumulator rebuilds the complete message at the end
 * so tool calls (which arrive as JSON fragments) can be read whole.
 * Adaptive thinking is the model's default, so no thinking block is
 * configured; effort is the one dial exposed.
 */
public class ClaudeAssistantModel implements AssistantModel {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAssistantModel.class);
    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    private final AnthropicClient client;
    private final AssistantProperties props;

    public ClaudeAssistantModel(AnthropicClient client, AssistantProperties props) {
        this.client = client;
        this.props = props;
    }

    @Override
    public ModelTurn run(String system, List<ChatMessage> messages, List<ToolSpec> tools, Consumer<String> onText) {
        MessageCreateParams.Builder params = MessageCreateParams.builder()
                .model(props.model())
                .maxTokens(props.maxTokens())
                .system(system)
                .outputConfig(OutputConfig.builder()
                        .effort(OutputConfig.Effort.of(props.effort()))
                        .build());
        if (!tools.isEmpty()) {
            params.toolChoice(ToolChoiceAuto.builder().build());
            tools.forEach(tool -> params.addTool(toTool(tool)));
        }
        messages.forEach(message -> params.addMessage(toParam(message)));

        try (StreamResponse<RawMessageStreamEvent> stream = client.messages().createStreaming(params.build())) {
            MessageAccumulator accumulator = MessageAccumulator.create();
            // onText may throw ClientGoneException; it leaves this block and
            // the try-with-resources closes the HTTP stream, so the model
            // stops generating for a browser that has already left
            stream.stream().forEach(event -> {
                accumulator.accumulate(event);
                event.contentBlockDelta()
                        .flatMap(delta -> delta.delta().text())
                        .ifPresent(text -> onText.accept(text.text()));
            });
            return toTurn(accumulator.message());
        } catch (AnthropicException e) {
            log.warn("Assistant model call failed: {}", e.getMessage());
            throw new AssistantUnavailableException("model call failed", e);
        }
    }

    static ModelTurn toTurn(Message message) {
        List<Part> parts = new ArrayList<>();
        for (ContentBlock block : message.content()) {
            block.text().ifPresent(text -> parts.add(new Part.Text(text.text())));
            block.toolUse().ifPresent(use -> parts.add(new Part.ToolUse(
                    use.id(), use.name(), use._input().convert(MAP))));
        }
        String stop = message.stopReason().map(reason -> reason.asString()).orElse("end_turn");
        ModelTurn.Outcome outcome = switch (stop) {
            case "tool_use" -> ModelTurn.Outcome.TOOL_USE;
            case "refusal" -> ModelTurn.Outcome.REFUSAL;
            case "max_tokens" -> ModelTurn.Outcome.MAX_TOKENS;
            default -> ModelTurn.Outcome.END;
        };
        return new ModelTurn(List.copyOf(parts), outcome,
                message.usage().inputTokens(), message.usage().outputTokens());
    }

    static Tool toTool(ToolSpec spec) {
        Tool.InputSchema.Properties.Builder properties = Tool.InputSchema.Properties.builder();
        spec.properties().forEach((name, schema) -> properties.putAdditionalProperty(name, JsonValue.from(schema)));
        return Tool.builder()
                .name(spec.name())
                .description(spec.description())
                .inputSchema(Tool.InputSchema.builder()
                        .properties(properties.build())
                        .required(spec.required())
                        .build())
                .build();
    }

    static MessageParam toParam(ChatMessage message) {
        List<ContentBlockParam> blocks = new ArrayList<>();
        for (Part part : message.parts()) {
            switch (part) {
                case Part.Text text -> {
                    if (!text.text().isBlank()) {
                        blocks.add(ContentBlockParam.ofText(TextBlockParam.builder().text(text.text()).build()));
                    }
                }
                case Part.ToolUse use -> {
                    ToolUseBlockParam.Input.Builder input = ToolUseBlockParam.Input.builder();
                    use.input().forEach((key, value) -> input.putAdditionalProperty(key, JsonValue.from(value)));
                    blocks.add(ContentBlockParam.ofToolUse(ToolUseBlockParam.builder()
                            .id(use.id()).name(use.name()).input(input.build()).build()));
                }
                case Part.ToolResult result -> blocks.add(ContentBlockParam.ofToolResult(
                        ToolResultBlockParam.builder().toolUseId(result.toolUseId()).content(result.text()).build()));
            }
        }
        return MessageParam.builder()
                .role(message.role() == ChatMessage.Role.USER ? MessageParam.Role.USER : MessageParam.Role.ASSISTANT)
                .contentOfBlockParams(blocks)
                .build();
    }
}
