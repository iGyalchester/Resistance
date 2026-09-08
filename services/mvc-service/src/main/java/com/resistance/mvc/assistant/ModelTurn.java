package com.resistance.mvc.assistant;

import java.util.List;

/**
 * What one call to the model produced: its parts (text and/or tool
 * calls), why it stopped, and what it cost in tokens.
 */
public record ModelTurn(List<Part> parts, Outcome outcome, long inputTokens, long outputTokens) {

    public enum Outcome {
        /** A finished answer. */
        END,
        /** The model wants tool results before it can finish. */
        TOOL_USE,
        /** The model declined the request outright. */
        REFUSAL,
        /** The answer hit the length cap. */
        MAX_TOKENS
    }

    public String text() {
        StringBuilder sb = new StringBuilder();
        for (Part part : parts) {
            if (part instanceof Part.Text t) {
                sb.append(t.text());
            }
        }
        return sb.toString();
    }
}
