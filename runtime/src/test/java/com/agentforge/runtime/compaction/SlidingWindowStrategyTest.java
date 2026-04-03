package com.agentforge.runtime.compaction;

import com.agentforge.common.model.ConversationMessage;
import com.agentforge.common.model.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlidingWindowStrategyTest {

    @Test
    @DisplayName("keeps last N messages when session exceeds window")
    void compact_largeSession_keepsLastN() {
        SlidingWindowStrategy strategy = new SlidingWindowStrategy(3);
        Session session = buildSession(5);
        Session compacted = strategy.compact(session, 200_000);
        assertThat(compacted.messages()).hasSize(3);
        // Should be the last 3 messages
        assertThat(compacted.messages().get(0).textContent()).contains("msg-2");
        assertThat(compacted.messages().get(2).textContent()).contains("msg-4");
    }

    @Test
    @DisplayName("returns same session when smaller than window")
    void compact_smallSession_returnsUnchanged() {
        SlidingWindowStrategy strategy = new SlidingWindowStrategy(10);
        Session session = buildSession(3);
        Session compacted = strategy.compact(session, 200_000);
        assertThat(compacted.messages()).hasSize(3);
    }

    @Test
    @DisplayName("exact window size returns all messages")
    void compact_exactWindowSize_returnsAll() {
        SlidingWindowStrategy strategy = new SlidingWindowStrategy(5);
        Session session = buildSession(5);
        Session compacted = strategy.compact(session, 200_000);
        assertThat(compacted.messages()).hasSize(5);
    }

    @Test
    @DisplayName("empty session returns unchanged")
    void compact_emptySession_returnsUnchanged() {
        SlidingWindowStrategy strategy = new SlidingWindowStrategy(5);
        Session session = Session.empty("s1");
        Session compacted = strategy.compact(session, 200_000);
        assertThat(compacted.messages()).isEmpty();
    }

    @Test
    @DisplayName("window size of 1 keeps only last message")
    void compact_windowSizeOne_keepsOnlyLast() {
        SlidingWindowStrategy strategy = new SlidingWindowStrategy(1);
        Session session = buildSession(4);
        Session compacted = strategy.compact(session, 200_000);
        assertThat(compacted.messages()).hasSize(1);
        assertThat(compacted.messages().get(0).textContent()).contains("msg-3");
    }

    @Test
    @DisplayName("invalid window size throws IllegalArgumentException")
    void constructor_invalidWindowSize_throws() {
        assertThatThrownBy(() -> new SlidingWindowStrategy(0))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new SlidingWindowStrategy(-1))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("name returns sliding-window")
    void name_returnsCorrectName() {
        assertThat(new SlidingWindowStrategy().name()).isEqualTo("sliding-window");
    }

    @Test
    @DisplayName("estimatedCost returns 0.0")
    void estimatedCost_returnsZero() {
        assertThat(new SlidingWindowStrategy().estimatedCost()).isEqualTo(0.0);
    }

    private Session buildSession(int count) {
        Session session = Session.empty("s1");
        for (int i = 0; i < count; i++) {
            session = session.addMessage(ConversationMessage.userText("msg-" + i));
        }
        return session;
    }
}
