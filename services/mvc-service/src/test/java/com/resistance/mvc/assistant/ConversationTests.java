package com.resistance.mvc.assistant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationTests {

    @Test
    void trimsOldestTurnsInPairsByCount() {
        Conversation c = new Conversation();
        for (int i = 1; i <= 4; i++) {
            c.add(ChatMessage.Role.USER, "q" + i);
            c.add(ChatMessage.Role.ASSISTANT, "a" + i);
        }
        c.trim(4, 10_000);

        assertThat(c.turns()).extracting(Conversation.Turn::text).containsExactly("q3", "a3", "q4", "a4");
        assertThat(c.turns().getFirst().role()).isEqualTo(ChatMessage.Role.USER);
    }

    @Test
    void trimsByCharactersToo() {
        Conversation c = new Conversation();
        c.add(ChatMessage.Role.USER, "x".repeat(100));
        c.add(ChatMessage.Role.ASSISTANT, "y".repeat(100));
        c.add(ChatMessage.Role.USER, "short");
        c.add(ChatMessage.Role.ASSISTANT, "reply");
        c.trim(20, 50);

        assertThat(c.turns()).extracting(Conversation.Turn::text).containsExactly("short", "reply");
    }

    @Test
    void clearEmptiesAndNullTextIsStoredAsEmpty() {
        Conversation c = new Conversation();
        c.add(ChatMessage.Role.ASSISTANT, null);
        assertThat(c.turns().getFirst().text()).isEmpty();
        c.clear();
        assertThat(c.turns()).isEmpty();
    }
}
