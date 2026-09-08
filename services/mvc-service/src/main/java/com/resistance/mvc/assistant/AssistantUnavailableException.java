package com.resistance.mvc.assistant;

/** The model provider answered with an error or could not be reached. Retryable. */
public class AssistantUnavailableException extends RuntimeException {

    public AssistantUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
