package com.resistance.mvc.assistant;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;

/**
 * A scripted model: each call pops the next turn, streams its text in
 * two halves (so tests see the delta path), and records what it was
 * asked. Throws when the script runs dry or when told to fail.
 */
class FakeAssistantModel implements AssistantModel {

    record Call(String system, List<ChatMessage> messages, List<ToolSpec> tools) {
    }

    private final Deque<Object> script = new ArrayDeque<>();
    final List<Call> calls = new ArrayList<>();

    FakeAssistantModel then(ModelTurn turn) {
        script.add(turn);
        return this;
    }

    FakeAssistantModel thenThrow(RuntimeException e) {
        script.add(e);
        return this;
    }

    static ModelTurn answer(String text) {
        return new ModelTurn(List.of(new Part.Text(text)), ModelTurn.Outcome.END, 100, 20);
    }

    static ModelTurn toolCall(String text, Part.ToolUse... uses) {
        List<Part> parts = new ArrayList<>();
        if (text != null) {
            parts.add(new Part.Text(text));
        }
        parts.addAll(List.of(uses));
        return new ModelTurn(parts, ModelTurn.Outcome.TOOL_USE, 100, 20);
    }

    static ModelTurn refusal() {
        return new ModelTurn(List.of(), ModelTurn.Outcome.REFUSAL, 10, 0);
    }

    @Override
    public ModelTurn run(String system, List<ChatMessage> messages, List<ToolSpec> tools, Consumer<String> onText) {
        calls.add(new Call(system, List.copyOf(messages), List.copyOf(tools)));
        Object next = script.poll();
        if (next == null) {
            throw new IllegalStateException("fake model has no scripted turn left");
        }
        if (next instanceof RuntimeException e) {
            throw e;
        }
        ModelTurn turn = (ModelTurn) next;
        String text = turn.text();
        if (!text.isEmpty()) {
            int half = text.length() / 2;
            onText.accept(text.substring(0, half));
            onText.accept(text.substring(half));
        }
        return turn;
    }
}
