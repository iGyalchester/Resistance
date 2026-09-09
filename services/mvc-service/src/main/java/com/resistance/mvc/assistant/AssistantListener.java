package com.resistance.mvc.assistant;

/**
 * How a reply reaches the caller while it is still being produced. The
 * controller turns each call into a server-sent event of the same name.
 */
public interface AssistantListener {

    /** A fragment of the answer's text, in order. */
    void onDelta(String text);

    /** A change the model proposes; the user decides. */
    void onAction(Proposal proposal);

    /** The reply is complete. */
    void onDone(Usage usage);

    /**
     * The reply failed. Codes: rate_limited, assistant_unavailable,
     * assistant_disabled, internal. Never a stack trace.
     */
    void onError(String code);

    record Usage(long inputTokens, long outputTokens) {
    }
}
