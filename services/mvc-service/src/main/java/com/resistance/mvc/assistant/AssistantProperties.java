package com.resistance.mvc.assistant;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The assistant's knobs, all under {@code tracker.ai.*}. The feature is on
 * exactly when an API key is present: no key, no model calls, and the UI
 * hides the chat. Everything else is a cost or safety limit.
 *
 * @param apiKey             Anthropic API key (env ANTHROPIC_API_KEY); blank = disabled
 * @param model              model id sent to the API
 * @param effort             how hard the model thinks: low / medium / high
 * @param maxTokens          cap on one answer's length
 * @param maxMessagesPerHour per-account rate limit, so one user cannot run up the bill
 * @param maxHistoryTurns    how many past turns are replayed with each message
 * @param maxHistoryChars    a second cap on replayed history, in characters
 */
@ConfigurationProperties("tracker.ai")
public record AssistantProperties(
        String apiKey,
        String model,
        String effort,
        int maxTokens,
        int maxMessagesPerHour,
        int maxHistoryTurns,
        int maxHistoryChars) {

    public boolean enabled() {
        return apiKey != null && !apiKey.isBlank();
    }
}
