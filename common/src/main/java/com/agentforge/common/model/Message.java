package com.agentforge.common.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable message in a conversation. Content blocks allow mixed text/tool-use.
 */
public record Message(
    String id,
    Role role,
    List<ContentBlock> content,
    Instant timestamp
) {
    public Message {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(role, "role must not be null");
        content = content != null ? List.copyOf(content) : List.of();
        timestamp = timestamp != null ? timestamp : Instant.now();
    }

    public static Message user(String id, String text) {
        return new Message(id, Role.USER, List.of(ContentBlock.text(text)), Instant.now());
    }

    public static Message assistant(String id, String text) {
        return new Message(id, Role.ASSISTANT, List.of(ContentBlock.text(text)), Instant.now());
    }

    public static Message system(String id, String text) {
        return new Message(id, Role.SYSTEM, List.of(ContentBlock.text(text)), Instant.now());
    }

    public String textContent() {
        return content.stream()
            .filter(b -> b instanceof ContentBlock.Text)
            .map(b -> ((ContentBlock.Text) b).text())
            .reduce("", (a, b) -> a.isEmpty() ? b : a + "\n" + b);
    }
}
