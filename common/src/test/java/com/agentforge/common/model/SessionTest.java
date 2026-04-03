package com.agentforge.common.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionTest {

    @Test
    void empty_hasZeroMessages() {
        Session session = Session.empty("s1");
        assertThat(session.messageCount()).isZero();
        assertThat(session.messages()).isEmpty();
    }

    @Test
    void empty_setsVersion() {
        Session session = Session.empty("s1");
        assertThat(session.version()).isEqualTo(Session.CURRENT_VERSION);
    }

    @Test
    void empty_setsId() {
        Session session = Session.empty("my-id");
        assertThat(session.id()).isEqualTo("my-id");
    }

    @Test
    void empty_totalUsageIsZero() {
        Session session = Session.empty("s1");
        assertThat(session.totalUsage()).isEqualTo(TokenUsage.ZERO);
    }

    @Test
    void addMessage_returnsNewSession() {
        Session session = Session.empty("s1");
        ConversationMessage msg = ConversationMessage.userText("hello");
        Session updated = session.addMessage(msg);
        assertThat(updated).isNotSameAs(session);
    }

    @Test
    void addMessage_incrementsCount() {
        Session session = Session.empty("s1");
        session = session.addMessage(ConversationMessage.userText("hello"));
        assertThat(session.messageCount()).isEqualTo(1);
        session = session.addMessage(ConversationMessage.assistantText("hi"));
        assertThat(session.messageCount()).isEqualTo(2);
    }

    @Test
    void addMessage_originalUnchanged() {
        Session original = Session.empty("s1");
        original.addMessage(ConversationMessage.userText("hello"));
        assertThat(original.messageCount()).isZero();
    }

    @Test
    void addMessage_accumulatesUsage() {
        Session session = Session.empty("s1");
        ConversationMessage msg = new ConversationMessage("user",
                List.of(new ContentBlock.Text("hi")),
                new TokenUsage(10, 5, 0, 0));
        session = session.addMessage(msg);
        assertThat(session.totalUsage().inputTokens()).isEqualTo(10);
        assertThat(session.totalUsage().outputTokens()).isEqualTo(5);
    }

    @Test
    void defensiveCopy_messagesAreImmutable() {
        List<ConversationMessage> mutable = new ArrayList<>();
        mutable.add(ConversationMessage.userText("first"));
        Session session = new Session(1, "s1", mutable, TokenUsage.ZERO);
        mutable.add(ConversationMessage.userText("second"));
        assertThat(session.messageCount()).isEqualTo(1);
    }

    @Test
    void messages_listIsUnmodifiable() {
        Session session = Session.empty("s1").addMessage(ConversationMessage.userText("hi"));
        assertThatThrownBy(() -> session.messages().add(ConversationMessage.userText("extra")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void currentVersion_isOne() {
        assertThat(Session.CURRENT_VERSION).isEqualTo(1);
    }
}
