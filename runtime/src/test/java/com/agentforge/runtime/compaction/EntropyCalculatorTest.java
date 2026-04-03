package com.agentforge.runtime.compaction;

import com.agentforge.common.model.ContentBlock;
import com.agentforge.common.model.ConversationMessage;
import com.agentforge.common.model.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class EntropyCalculatorTest {

    private EntropyCalculator calc;

    @BeforeEach
    void setUp() {
        calc = new EntropyCalculator();
    }

    @Test
    @DisplayName("empty string returns 0")
    void entropy_emptyString_returnsZero() {
        assertThat(calc.entropy("")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("null string returns 0")
    void entropy_nullString_returnsZero() {
        assertThat(calc.entropy(null)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("single repeated word returns near-zero entropy")
    void entropy_singleRepeatedWord_returnsZero() {
        assertThat(calc.entropy("the the the the the the the the")).isCloseTo(0.0, within(0.01));
    }

    @Test
    @DisplayName("single word returns 0")
    void entropy_singleWord_returnsZero() {
        assertThat(calc.entropy("hello")).isEqualTo(0.0);
    }

    @Test
    @DisplayName("uniform distribution of distinct words returns high entropy")
    void entropy_uniformDistribution_returnsHighEntropy() {
        // 8 unique words, each appearing once — max entropy
        String text = "alpha beta gamma delta epsilon zeta eta theta";
        double entropy = calc.entropy(text);
        assertThat(entropy).isGreaterThan(0.9);
    }

    @Test
    @DisplayName("normal mixed text returns medium entropy")
    void entropy_normalText_returnsMediumEntropy() {
        String text = "The quick brown fox jumps over the lazy dog near the river";
        double entropy = calc.entropy(text);
        assertThat(entropy).isBetween(0.1, 0.99);
    }

    @Test
    @DisplayName("entropy is normalized to [0, 1]")
    void entropy_alwaysInRange() {
        assertThat(calc.entropy("a b c d e f g")).isBetween(0.0, 1.0);
        assertThat(calc.entropy("x x x x x x x")).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("messageEntropy aggregates text from all blocks")
    void messageEntropy_aggregatesAllBlocks() {
        ConversationMessage msg = ConversationMessage.of("user", List.of(
            new ContentBlock.Text("alpha beta gamma delta"),
            new ContentBlock.ToolResult("id1", "epsilon zeta eta", false)
        ));
        double entropy = calc.messageEntropy(msg);
        assertThat(entropy).isGreaterThan(0.0);
    }

    @Test
    @DisplayName("messageEntropy for null message returns 0")
    void messageEntropy_nullMessage_returnsZero() {
        assertThat(calc.messageEntropy(null)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("sessionEntropy averages over all messages")
    void sessionEntropy_averagesMessages() {
        Session session = Session.empty("s1")
            .addMessage(ConversationMessage.userText("hello world"))
            .addMessage(ConversationMessage.userText("alpha beta gamma delta epsilon"));
        double entropy = calc.sessionEntropy(session);
        assertThat(entropy).isGreaterThanOrEqualTo(0.0);
        assertThat(entropy).isLessThanOrEqualTo(1.0);
    }

    @Test
    @DisplayName("sessionEntropy for empty session returns 0")
    void sessionEntropy_emptySession_returnsZero() {
        assertThat(calc.sessionEntropy(Session.empty("s1"))).isEqualTo(0.0);
    }
}
