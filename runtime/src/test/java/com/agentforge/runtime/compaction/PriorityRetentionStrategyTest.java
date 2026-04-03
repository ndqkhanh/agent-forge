package com.agentforge.runtime.compaction;

import com.agentforge.common.model.ContentBlock;
import com.agentforge.common.model.ConversationMessage;
import com.agentforge.common.model.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PriorityRetentionStrategyTest {

    private PriorityRetentionStrategy strategy;

    @BeforeEach
    void setUp() {
        strategy = new PriorityRetentionStrategy();
    }

    @Test
    @DisplayName("tool result messages are prioritized over plain assistant text")
    void compact_toolResultsHigherPriorityThanAssistantText() {
        // Build a session where tool-result messages should survive aggressive compaction
        Session session = Session.empty("s1");
        // Add several low-priority assistant text messages
        for (int i = 0; i < 5; i++) {
            session = session.addMessage(ConversationMessage.assistantText("some response " + i));
        }
        // Add a tool-result message
        session = session.addMessage(ConversationMessage.of("user",
            List.of(ContentBlock.ToolResult.success("id1", "important tool output"))));

        Session compacted = strategy.compact(session, 1000); // tiny context to force pruning
        // Tool result message should be retained
        boolean hasToolResult = compacted.messages().stream()
            .anyMatch(m -> m.blocks().stream().anyMatch(b -> b instanceof ContentBlock.ToolResult));
        assertThat(hasToolResult).isTrue();
    }

    @Test
    @DisplayName("system messages are always retained")
    void compact_systemMessagesAlwaysRetained() {
        Session session = Session.empty("s1");
        session = session.addMessage(ConversationMessage.of("system",
            List.of(new ContentBlock.Text("You are a helpful assistant."))));
        for (int i = 0; i < 10; i++) {
            session = session.addMessage(ConversationMessage.userText("question " + i));
        }

        Session compacted = strategy.compact(session, 500);
        boolean hasSystem = compacted.messages().stream()
            .anyMatch(m -> "system".equals(m.role()));
        assertThat(hasSystem).isTrue();
    }

    @Test
    @DisplayName("empty session returns unchanged")
    void compact_emptySession_returnsUnchanged() {
        Session session = Session.empty("s1");
        Session compacted = strategy.compact(session, 200_000);
        assertThat(compacted.messages()).isEmpty();
    }

    @Test
    @DisplayName("small session within budget returns all messages")
    void compact_sessionWithinBudget_returnsAll() {
        Session session = Session.empty("s1")
            .addMessage(ConversationMessage.userText("hello"))
            .addMessage(ConversationMessage.assistantText("hi"));
        Session compacted = strategy.compact(session, 200_000);
        assertThat(compacted.messages()).hasSize(2);
    }

    @Test
    @DisplayName("name returns priority-retention")
    void name_returnsCorrectName() {
        assertThat(strategy.name()).isEqualTo("priority-retention");
    }

    @Test
    @DisplayName("estimatedCost returns 0.1")
    void estimatedCost_returnsExpectedValue() {
        assertThat(strategy.estimatedCost()).isEqualTo(0.1);
    }

    @Test
    @DisplayName("compacted session preserves chronological order")
    void compact_preservesChronologicalOrder() {
        Session session = Session.empty("s1");
        session = session.addMessage(ConversationMessage.userText("first"));
        session = session.addMessage(ConversationMessage.assistantText("second"));
        session = session.addMessage(ConversationMessage.userText("third"));

        Session compacted = strategy.compact(session, 200_000);
        List<ConversationMessage> msgs = compacted.messages();
        // Verify first appears before last
        assertThat(msgs).isNotEmpty();
        if (msgs.size() >= 2) {
            int firstIdx = -1, thirdIdx = -1;
            for (int i = 0; i < msgs.size(); i++) {
                String text = msgs.get(i).textContent();
                if (text.contains("first")) firstIdx = i;
                if (text.contains("third")) thirdIdx = i;
            }
            if (firstIdx >= 0 && thirdIdx >= 0) {
                assertThat(firstIdx).isLessThan(thirdIdx);
            }
        }
    }
}
