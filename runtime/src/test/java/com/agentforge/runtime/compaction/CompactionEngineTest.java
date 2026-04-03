package com.agentforge.runtime.compaction;

import com.agentforge.common.model.ConversationMessage;
import com.agentforge.common.model.Session;
import com.agentforge.common.model.TokenUsage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CompactionEngineTest {

    @Test
    @DisplayName("shouldCompact returns false when below threshold")
    void shouldCompact_belowThreshold_returnsFalse() {
        CompactionEngine engine = CompactionEngine.withDefaults();
        // 1% token pressure — well below 70% default threshold
        Session session = sessionWithUsage(2_000);
        assertThat(engine.shouldCompact(session, 200_000)).isFalse();
    }

    @Test
    @DisplayName("shouldCompact returns true when above threshold")
    void shouldCompact_aboveThreshold_returnsTrue() {
        CompactionEngine engine = CompactionEngine.withDefaults();
        // 85% token pressure — above 70% default threshold
        Session session = sessionWithUsage(170_000);
        assertThat(engine.shouldCompact(session, 200_000)).isTrue();
    }

    @Test
    @DisplayName("shouldCompact returns false for empty session")
    void shouldCompact_emptySession_returnsFalse() {
        CompactionEngine engine = CompactionEngine.withDefaults();
        assertThat(engine.shouldCompact(Session.empty("s1"), 200_000)).isFalse();
    }

    @Test
    @DisplayName("compact reduces message count for large session")
    void compact_largeSession_reducesMessageCount() {
        CompactionEngine engine = CompactionEngine.withDefaults();
        Session session = Session.empty("s1");
        for (int i = 0; i < 30; i++) {
            session = session.addMessage(ConversationMessage.userText("message " + i));
        }
        Session compacted = engine.compact(session, 200_000);
        assertThat(compacted.messages().size()).isLessThan(session.messages().size());
    }

    @Test
    @DisplayName("compact delegates to selected strategy and returns non-null")
    void compact_delegatesToStrategy_returnsNonNull() {
        CompactionEngine engine = CompactionEngine.withDefaults();
        Session session = Session.empty("s1")
            .addMessage(ConversationMessage.userText("hello"))
            .addMessage(ConversationMessage.assistantText("hi"));
        Session compacted = engine.compact(session, 200_000);
        assertThat(compacted).isNotNull();
    }

    @Test
    @DisplayName("withDefaults factory creates engine with valid configuration")
    void withDefaults_createsValidEngine() {
        CompactionEngine engine = CompactionEngine.withDefaults();
        assertThat(engine).isNotNull();
        // Verify basic functionality works
        assertThat(engine.shouldCompact(Session.empty("s1"), 200_000)).isFalse();
    }

    private Session sessionWithUsage(int tokens) {
        TokenUsage usage = TokenUsage.of(tokens, 0);
        ConversationMessage msg = new ConversationMessage("user",
            List.of(new com.agentforge.common.model.ContentBlock.Text("x")), usage);
        return Session.empty("s1").addMessage(msg);
    }
}
