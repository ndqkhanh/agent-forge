package com.agentforge.common.model;

import java.util.List;

/**
 * A single message in a conversation, with role, content blocks, and optional token usage.
 * Immutable record. Thread-safe.
 */
public record ConversationMessage(
    String role,
    List<ContentBlock> blocks,
    TokenUsage usage
) {
    public ConversationMessage {
        if (role == null || role.isBlank()) throw new IllegalArgumentException("role must not be blank");
        if (blocks == null) throw new IllegalArgumentException("blocks must not be null");
        blocks = List.copyOf(blocks);
    }

    public static ConversationMessage of(String role, List<ContentBlock> blocks) {
        return new ConversationMessage(role, blocks, TokenUsage.ZERO);
    }

    public static ConversationMessage userText(String text) {
        return of("user", List.of(new ContentBlock.Text(text)));
    }

    public static ConversationMessage assistantText(String text) {
        return of("assistant", List.of(new ContentBlock.Text(text)));
    }

    public String textContent() {
        StringBuilder sb = new StringBuilder();
        for (ContentBlock block : blocks) {
            if (block instanceof ContentBlock.Text t) {
                sb.append(t.text());
            }
        }
        return sb.toString();
    }
}
