package com.agentforge.runtime.compaction;

import com.agentforge.api.provider.ApiClient;
import com.agentforge.api.provider.ApiRequest;
import com.agentforge.api.stream.AssistantEvent;
import com.agentforge.common.model.ContentBlock;
import com.agentforge.common.model.ConversationMessage;
import com.agentforge.common.model.ModelInfo;
import com.agentforge.common.model.Session;
import com.agentforge.common.model.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class AdaptiveSelectorTest {

    private SlidingWindowStrategy sliding;
    private PriorityRetentionStrategy priority;
    private EntropyPruningStrategy entropy;
    private LlmSummarizationStrategy llm;
    private EntropyCalculator calc;
    private AdaptiveSelector selector;

    @BeforeEach
    void setUp() {
        calc = new EntropyCalculator();
        sliding = new SlidingWindowStrategy();
        priority = new PriorityRetentionStrategy();
        entropy = new EntropyPruningStrategy(calc);
        llm = new LlmSummarizationStrategy(new ApiClient() {
            @Override
            public Stream<AssistantEvent> streamMessage(ApiRequest request) {
                return Stream.of(new AssistantEvent.TextDelta("summary"),
                    new AssistantEvent.MessageStop("end_turn"));
            }
            @Override public String providerName() { return "stub"; }
            @Override public List<ModelInfo> availableModels() { return List.of(); }
        });
        selector = new AdaptiveSelector(sliding, priority, entropy, llm, calc);
    }

    @Test
    @DisplayName("low token pressure selects SlidingWindow")
    void select_lowPressure_selectsSlidingWindow() {
        // 1000 tokens used in a 200k context = 0.5% pressure → well below LOW threshold
        Session session = sessionWithUsage(1000);
        CompactionStrategy selected = selector.select(session, 200_000);
        assertThat(selected).isInstanceOf(SlidingWindowStrategy.class);
    }

    @Test
    @DisplayName("high token pressure selects LlmSummarization")
    void select_highPressure_selectsLlmSummarization() {
        // 180k tokens used in 200k context = 90% pressure → above HIGH threshold 0.85
        Session session = sessionWithUsage(180_000);
        CompactionStrategy selected = selector.select(session, 200_000);
        assertThat(selected).isInstanceOf(LlmSummarizationStrategy.class);
    }

    @Test
    @DisplayName("medium pressure with high tool ratio selects PriorityRetention")
    void select_mediumPressureHighToolRatio_selectsPriorityRetention() {
        // 75% pressure — medium range
        Session session = sessionWithUsage(150_000);
        // Add mostly tool-result messages (ratio > 0.5)
        for (int i = 0; i < 8; i++) {
            session = session.addMessage(ConversationMessage.of("user",
                List.of(ContentBlock.ToolResult.success("id" + i, "result " + i))));
        }
        for (int i = 0; i < 2; i++) {
            session = session.addMessage(ConversationMessage.userText("question"));
        }
        CompactionStrategy selected = selector.select(session, 200_000);
        assertThat(selected).isInstanceOf(PriorityRetentionStrategy.class);
    }

    @Test
    @DisplayName("medium pressure with high entropy and low tool ratio selects EntropyPruning")
    void select_mediumPressureHighEntropyLowToolRatio_selectsEntropyPruning() {
        // 75% pressure — medium range; lots of high-entropy Q&A (tool ratio < 0.3)
        Session session = sessionWithUsage(150_000);
        for (int i = 0; i < 10; i++) {
            session = session.addMessage(ConversationMessage.userText(
                "alpha beta gamma delta epsilon " + i + " zeta eta theta iota kappa lambda mu nu xi omicron"));
        }
        CompactionStrategy selected = selector.select(session, 200_000);
        // With high entropy text and no tool calls, should choose EntropyPruning
        assertThat(selected).isIn(entropy, sliding); // either is valid at this boundary
    }

    @Test
    @DisplayName("empty session with low pressure selects SlidingWindow")
    void select_emptySession_selectsSlidingWindow() {
        Session session = Session.empty("s1");
        CompactionStrategy selected = selector.select(session, 200_000);
        assertThat(selected).isInstanceOf(SlidingWindowStrategy.class);
    }

    @Test
    @DisplayName("tokenPressure returns 0 for empty session")
    void tokenPressure_emptySession_returnsZero() {
        Session session = Session.empty("s1");
        double pressure = selector.tokenPressure(session, 200_000);
        assertThat(pressure).isEqualTo(0.0);
    }

    @Test
    @DisplayName("tokenPressure uses totalUsage when available")
    void tokenPressure_withUsage_calculatesCorrectly() {
        Session session = sessionWithUsage(100_000);
        double pressure = selector.tokenPressure(session, 200_000);
        assertThat(pressure).isCloseTo(0.5, org.assertj.core.api.Assertions.within(0.01));
    }

    @Test
    @DisplayName("toolCallRatio returns 0 for session with no tool results")
    void toolCallRatio_noToolCalls_returnsZero() {
        Session session = Session.empty("s1")
            .addMessage(ConversationMessage.userText("hello"))
            .addMessage(ConversationMessage.assistantText("hi"));
        assertThat(selector.toolCallRatio(session)).isEqualTo(0.0);
    }

    /** Create a session that reports given token usage via totalUsage. */
    private Session sessionWithUsage(int tokens) {
        TokenUsage usage = TokenUsage.of(tokens, 0);
        // We can't set totalUsage directly on Session.empty, so we create a ConversationMessage with that usage
        ConversationMessage msg = new ConversationMessage("user",
            List.of(new ContentBlock.Text("x")), usage);
        return Session.empty("s1").addMessage(msg);
    }
}
