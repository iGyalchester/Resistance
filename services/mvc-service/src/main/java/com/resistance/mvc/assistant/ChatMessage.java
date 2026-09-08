package com.resistance.mvc.assistant;

import java.util.List;

/** A message in the exchange with the model: who said it and its parts. */
public record ChatMessage(Role role, List<Part> parts) {

    public enum Role { USER, ASSISTANT }

    public static ChatMessage user(String text) {
        return new ChatMessage(Role.USER, List.of(new Part.Text(text)));
    }

    public static ChatMessage assistant(String text) {
        return new ChatMessage(Role.ASSISTANT, List.of(new Part.Text(text)));
    }
}
