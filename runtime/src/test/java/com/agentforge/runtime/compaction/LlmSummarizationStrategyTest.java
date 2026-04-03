package com.agentforge.runtime.compaction;

import com.agentforge.api.provider.ApiClient;
import com.agentforge.api.provider.ApiRequest;
import com.agentforge.api.stream.AssistantEvent;
import com.agentforge.common.model.ConversationMessage;
import com.agentforge.common.model.ModelInfo;
import com.agentforge.common.model.Session;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class LlmSummarizationStrategyTest {

    /** Stub ApiClient that returns a fixed summary text. */
    private static ApiClient stubClient(String summaryText) {
        return new ApiClient() {
            @Override
            public Stream<AssistantEvent> streamMessage(ApiRequest request) {
                return Stream.of(
                    new AssistantEvent.TextDelta(summaryText),
                    new AssistantEvent.MessageStop("end_turn")
                );
            }
            @Override public String providerName() { return "stub"; }
            @Override public List<ModelInfo> availableModels() { return List.of(); }
        };
    }

    @Test
    @DisplayName("summarizes early messages and keeps recent messages intact")
    void compact_summarizesEarlyKeepsRecent() {
        ApiClient client = stubClient("SUMMARY OF EARLY MESSAGES");
        LlmSummarizationStrategy strategy = new LlmSummarizationStrategy(client);

        Session session = Session.empty("s1");
        // Add 15 messages so we exceed the RECENT_MESSAGES_TO_KEEP threshold of 10
        for (int i = 0; i < 15; i++) {
            session = session.addMessage(ConversationMessage.userText("message " + i));
        }

        Session compacted = strategy.compact(session, 200_000);

        // Should have fewer messages than original
        assertThat(compacted.messages().size()).isLessThan(session.messages().size());

        // First message should contain the summary
        assertThat(compacted.messages().get(0).textContent()).contains("SUMMARY OF EARLY MESSAGES");

        // Recent messages should still be present
        assertThat(compacted.messages().stream()
            .anyMatch(m -> m.textContent().contains("message 14"))).isTrue();
    }

    @Test
    @DisplayName("small session (below threshold) returns unchanged")
    void compact_smallSession_returnsUnchanged() {
        ApiClient client = stubClient("should not be called");
        LlmSummarizationStrategy strategy = new LlmSummarizationStrategy(client);

        Session session = Session.empty("s1");
        for (int i = 0; i < 5; i++) {
            session = session.addMessage(ConversationMessage.userText("message " + i));
        }

        Session compacted = strategy.compact(session, 200_000);
        assertThat(compacted.messages()).hasSize(5);
    }

    @Test
    @DisplayName("summary replaces multiple early messages with one summary block")
    void compact_summaryReplacesEarlyMessages() {
        ApiClient client = stubClient("Concise summary here.");
        LlmSummarizationStrategy strategy = new LlmSummarizationStrategy(client);

        Session session = Session.empty("s1");
        for (int i = 0; i < 20; i++) {
            session = session.addMessage(ConversationMessage.userText("msg " + i));
        }

        Session compacted = strategy.compact(session, 200_000);
        // Compacted = 1 summary message + 10 recent messages = 11 total
        assertThat(compacted.messages()).hasSize(11);
    }

    @Test
    @DisplayName("constructor rejects null ApiClient")
    void constructor_nullClient_throws() {
        assertThatThrownBy(() -> new LlmSummarizationStrategy(null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("name returns llm-summarization")
    void name_returnsCorrectName() {
        LlmSummarizationStrategy strategy = new LlmSummarizationStrategy(stubClient("x"));
        assertThat(strategy.name()).isEqualTo("llm-summarization");
    }

    @Test
    @DisplayName("estimatedCost returns 1.0")
    void estimatedCost_returnsOne() {
        LlmSummarizationStrategy strategy = new LlmSummarizationStrategy(stubClient("x"));
        assertThat(strategy.estimatedCost()).isEqualTo(1.0);
    }
}
