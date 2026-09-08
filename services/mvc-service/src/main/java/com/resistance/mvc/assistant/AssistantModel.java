package com.resistance.mvc.assistant;

import java.util.List;
import java.util.function.Consumer;

/**
 * The seam between the assistant and the model provider. One method: run
 * the conversation once, stream text as it arrives, and return the whole
 * turn at the end. Tests script a fake; production talks to Claude.
 */
public interface AssistantModel {

    /**
     * @param system   the system prompt (persona, rules, the user's data)
     * @param messages the conversation so far, ending with the user's message
     * @param tools    what the model may call
     * @param onText   called with each text fragment as it streams in
     * @throws AssistantUnavailableException when the provider fails or is unreachable
     */
    ModelTurn run(String system, List<ChatMessage> messages, List<ToolSpec> tools, Consumer<String> onText);
}
