package com.agentforge.common.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

class ConversationTest {

    @Test
    @DisplayName("New conversation is empty")
    void newConversationEmpty() {
        var conv = new Conversation("conv_1", "claude-3", null, null, null);
        assertThat(conv.isEmpty()).isTrue();
        assertThat(conv.messages()).isEmpty();
        assertThat(conv.lastMessage()).isNull();
    }

    @Test
    @DisplayName("appendMessage returns new conversation with message added")
    void appendMessage() {
        var conv = new Conversation("conv_1", "claude-3", null, null, null);
        var msg = Message.user("msg_1", "hello");
        var updated = conv.appendMessage(msg);

        assertThat(conv.isEmpty()).isTrue();
        assertThat(updated.isEmpty()).isFalse();
        assertThat(updated.messages()).hasSize(1);
        assertThat(updated.lastMessage()).isEqualTo(msg);
    }

    @Test
    @DisplayName("appendMessage preserves original conversation (immutability)")
    void appendPreservesOriginal() {
        var conv = new Conversation("conv_1", "claude-3", null, null, null);
        conv.appendMessage(Message.user("msg_1", "hello"));
        assertThat(conv.isEmpty()).isTrue();
    }

    @Test
    @DisplayName("withModel returns new conversation with updated model")
    void withModel() {
        var conv = new Conversation("conv_1", "claude-3", null, null, null);
        var updated = conv.withModel("gpt-4");
        assertThat(updated.model()).isEqualTo("gpt-4");
        assertThat(conv.model()).isEqualTo("claude-3");
    }

    @Test
    @DisplayName("tokenEstimate approximates based on char count / 4")
    void tokenEstimate() {
        var conv = new Conversation("conv_1", "claude-3", null, null, null)
            .appendMessage(Message.user("msg_1", "hello world")); // 11 chars / 4 = 2
        assertThat(conv.tokenEstimate()).isEqualTo(2);
    }

    @Test
    @DisplayName("lastMessage returns the final message")
    void lastMessage() {
        var conv = new Conversation("conv_1", "claude-3", null, null, null)
            .appendMessage(Message.user("msg_1", "first"))
            .appendMessage(Message.assistant("msg_2", "second"));
        assertThat(conv.lastMessage().textContent()).isEqualTo("second");
    }

    @Test
    @DisplayName("Rejects null id")
    void rejectsNullId() {
        assertThatThrownBy(() -> new Conversation(null, "claude-3", null, null, null))
            .isInstanceOf(NullPointerException.class);
    }
}
