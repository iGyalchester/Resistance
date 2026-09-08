package com.resistance.mvc.assistant;

import java.util.ArrayList;
import java.util.List;

/** Collects everything a reply emits, in order. */
class RecordingListener implements AssistantListener {

    final List<String> events = new ArrayList<>();
    final StringBuilder text = new StringBuilder();
    final List<Proposal> proposals = new ArrayList<>();
    Usage usage;
    String error;

    @Override
    public void onDelta(String delta) {
        events.add("delta");
        text.append(delta);
    }

    @Override
    public void onAction(Proposal proposal) {
        events.add("action");
        proposals.add(proposal);
    }

    @Override
    public void onDone(Usage usage) {
        events.add("done");
        this.usage = usage;
    }

    @Override
    public void onError(String code) {
        events.add("error");
        this.error = code;
    }
}
