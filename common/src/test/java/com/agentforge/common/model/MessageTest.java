package com.agentforge.common.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

class MessageTest {

    @Test
    @DisplayName("User message factory creates correct role and content")
    void userMessage() {
        var msg = Message.user("msg_1", "hello");
        assertThat(msg.id()).isEqualTo("msg_1");
        assertThat(msg.role()).isEqualTo(Role.USER);
        assertThat(msg.textContent()).isEqualTo("hello");
        assertThat(msg.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("Assistant message factory creates correct role")
    void assistantMessage() {
        var msg = Message.assistant("msg_2", "response");
        assertThat(msg.role()).isEqualTo(Role.ASSISTANT);
        assertThat(msg.textContent()).isEqualTo("response");
    }

    @Test
    @DisplayName("System message factory creates correct role")
    void systemMessage() {
        var msg = Message.system("msg_3", "instructions");
        assertThat(msg.role()).isEqualTo(Role.SYSTEM);
    }

    @Test
    @DisplayName("Content list is defensively copied")
    void contentImmutable() {
        var content = new java.util.ArrayList<ContentBlock>();
        content.add(ContentBlock.text("test"));
        var msg = new Message("msg_1", Role.USER, content, Instant.now());
        content.add(ContentBlock.text("mutated"));
        assertThat(msg.content()).hasSize(1);
    }

    @Test
    @DisplayName("Null content defaults to empty list")
    void nullContentDefaults() {
        var msg = new Message("msg_1", Role.USER, null, null);
        assertThat(msg.content()).isEmpty();
        assertThat(msg.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("textContent concatenates multiple text blocks")
    void textContentMultipleBlocks() {
        var msg = new Message("msg_1", Role.USER,
            List.of(ContentBlock.text("hello"), ContentBlock.text("world")), Instant.now());
        assertThat(msg.textContent()).isEqualTo("hello\nworld");
    }

    @Test
    @DisplayName("textContent ignores non-text blocks")
    void textContentIgnoresNonText() {
        var msg = new Message("msg_1", Role.USER,
            List.of(
                ContentBlock.text("hello"),
                ContentBlock.toolUse("tc_1", "bash", null)
            ), Instant.now());
        assertThat(msg.textContent()).isEqualTo("hello");
    }

    @Test
    @DisplayName("Rejects null id")
    void rejectsNullId() {
        assertThatThrownBy(() -> new Message(null, Role.USER, List.of(), Instant.now()))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("Rejects null role")
    void rejectsNullRole() {
        assertThatThrownBy(() -> new Message("msg_1", null, List.of(), Instant.now()))
            .isInstanceOf(NullPointerException.class);
    }
}
