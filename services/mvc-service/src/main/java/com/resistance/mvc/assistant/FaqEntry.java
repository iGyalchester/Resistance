package com.resistance.mvc.assistant;

/** One Help-page question and its answer, from assistant/faq.json. */
public record FaqEntry(String id, String question, String answer) {
}
