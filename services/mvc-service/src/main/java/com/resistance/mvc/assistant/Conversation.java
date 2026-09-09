package com.resistance.mvc.assistant;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * One browser session's chat history, kept on the HTTP session so it
 * disappears with logout and is never written to the database. Only the
 * spoken text is kept: tool calls and thinking are not replayed, which
 * keeps every follow-up message cheap and the history easy to reason
 * about. Trimmed by turn count and character count so a long chat cannot
 * grow the prompt without bound.
 */
public class Conversation implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public record Turn(ChatMessage.Role role, String text) implements Serializable {
    }

    private final ArrayList<Turn> turns = new ArrayList<>();

    public synchronized List<Turn> turns() {
        return List.copyOf(turns);
    }

    public synchronized void add(ChatMessage.Role role, String text) {
        turns.add(new Turn(role, text == null ? "" : text));
    }

    public synchronized void clear() {
        turns.clear();
    }

    /**
     * Drops the oldest turns until both caps hold. Turns are removed in
     * pairs where possible so the history keeps starting with the user.
     */
    public synchronized void trim(int maxTurns, int maxChars) {
        while (turns.size() > maxTurns || totalChars() > maxChars) {
            if (turns.isEmpty()) {
                return;
            }
            turns.removeFirst();
            if (!turns.isEmpty() && turns.getFirst().role() == ChatMessage.Role.ASSISTANT) {
                turns.removeFirst();
            }
        }
    }

    private int totalChars() {
        int total = 0;
        for (Turn turn : turns) {
            total += turn.text().length();
        }
        return total;
    }
}
