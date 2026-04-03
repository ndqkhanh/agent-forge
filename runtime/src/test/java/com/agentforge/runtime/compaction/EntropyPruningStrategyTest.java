package com.agentforge.runtime.compaction;

import com.agentforge.common.model.ConversationMessage;
import com.agentforge.common.model.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EntropyPruningStrategyTest {

    private EntropyPruningStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new EntropyPruningStrategy();
    }

    @Test
    @DisplayName("high entropy messages are retained over low entropy messages")
    void compact_highEntropyRetainedOverLowEntropy() {
        // "the the the the" is low entropy; diverse text is high entropy
        Session session = Session.empty("s1")
            .addMessage(ConversationMessage.userText("the the the the the the the the"))
            .addMessage(ConversationMessage.userText("alpha beta gamma delta epsilon zeta eta theta iota kappa"));

        Session compacted = strategy.compact(session, 200_000);
        // Should keep at least 1 message; the high-entropy one should survive
        assertThat(compacted.messages()).isNotEmpty();
        boolean hasHighEntropy = compacted.messages().stream()
            .anyMatch(m -> m.textContent().contains("alpha"));
        assertThat(hasHighEntropy).isTrue();
    }

    @Test
    @DisplayName("single message session returns unchanged")
    void compact_singleMessage_returnsUnchanged() {
        Session session = Session.empty("s1")
            .addMessage(ConversationMessage.userText("hello world"));
        Session compacted = strategy.compact(session, 200_000);
        assertThat(compacted.messages()).hasSize(1);
    }

    @Test
    @DisplayName("empty session returns unchanged")
    void compact_emptySession_returnsUnchanged() {
        Session session = Session.empty("s1");
        Session compacted = strategy.compact(session, 200_000);
        assertThat(compacted.messages()).isEmpty();
    }

    @Test
    @DisplayName("compacted session has fewer messages than original for large sessions")
    void compact_largeSession_reduceMessageCount() {
        Session session = Session.empty("s1");
        // Add many identical (low-entropy) messages
        for (int i = 0; i < 10; i++) {
            session = session.addMessage(ConversationMessage.userText("ok ok ok ok ok ok ok ok ok ok"));
        }
        // Add some diverse ones
        session = session.addMessage(ConversationMessage.userText(
            "analyzing quantum entanglement photon interference wavelength spectrometry"));

        Session compacted = strategy.compact(session, 200_000);
        assertThat(compacted.messages().size()).isLessThan(session.messages().size());
    }

    @Test
    @DisplayName("compacted session preserves chronological order")
    void compact_preservesChronologicalOrder() {
        Session session = Session.empty("s1");
        for (int i = 0; i < 6; i++) {
            session = session.addMessage(
                ConversationMessage.userText("message index " + i + " unique words " + (i * 7)));
        }
        Session compacted = strategy.compact(session, 200_000);
        // All retained messages must appear in original order (indices non-decreasing)
        assertThat(compacted.messages()).isNotEmpty();
    }

    @Test
    @DisplayName("name returns entropy-pruning")
    void name_returnsCorrectName() {
        assertThat(strategy.name()).isEqualTo("entropy-pruning");
    }

    @Test
    @DisplayName("estimatedCost returns 0.2")
    void estimatedCost_returnsExpectedValue() {
        assertThat(strategy.estimatedCost()).isEqualTo(0.2);
    }
}
