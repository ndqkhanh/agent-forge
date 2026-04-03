package com.agentforge.common.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.ArrayList;

/**
 * Immutable conversation — a sequence of messages with metadata.
 */
public record Conversation(
    String id,
    String model,
    List<Message> messages,
    Instant createdAt,
    Instant updatedAt
) {
    public Conversation {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(model, "model must not be null");
        messages = messages != null ? List.copyOf(messages) : List.of();
        createdAt = createdAt != null ? createdAt : Instant.now();
        updatedAt = updatedAt != null ? updatedAt : Instant.now();
    }

    public Conversation appendMessage(Message message) {
        var updated = new ArrayList<>(messages);
        updated.add(message);
        return new Conversation(id, model, updated, createdAt, Instant.now());
    }

    public Conversation withModel(String newModel) {
        return new Conversation(id, newModel, messages, createdAt, Instant.now());
    }

    public int tokenEstimate() {
        return messages.stream()
            .mapToInt(m -> m.textContent().length() / 4)
            .sum();
    }

    public boolean isEmpty() {
        return messages.isEmpty();
    }

    public Message lastMessage() {
        return messages.isEmpty() ? null : messages.getLast();
    }
}
