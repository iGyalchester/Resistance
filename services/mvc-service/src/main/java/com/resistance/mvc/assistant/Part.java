package com.resistance.mvc.assistant;

import java.util.Map;

/**
 * One piece of a chat message on the way to or from the model. Text is
 * what people read; the other two are the tool-use handshake: the model
 * asks to call a tool, we answer with a result, and the pair is replayed
 * to the model so it can finish its sentence.
 */
public sealed interface Part {

    record Text(String text) implements Part {
    }

    record ToolUse(String id, String name, Map<String, Object> input) implements Part {
    }

    record ToolResult(String toolUseId, String text) implements Part {
    }
}
