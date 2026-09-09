package com.resistance.mvc.assistant;

/** Thrown when the assistant is used without an API key configured. Maps to 503. */
public class AssistantDisabledException extends RuntimeException {

    public AssistantDisabledException() {
        super("assistant is not configured");
    }
}
