package com.agentforge.runtime;

import com.agentforge.common.model.TokenUsage;

import java.util.List;

/**
 * The result of executing a single conversation turn.
 * Immutable record. Thread-safe.
 */
public record TurnResult(
    String assistantText,
    List<ToolCall> toolCalls,
    TokenUsage turnUsage,
    int iterations,
    boolean wasCompacted
) {
    public TurnResult {
        if (assistantText == null) throw new IllegalArgumentException("assistantText must not be null");
        if (toolCalls == null) throw new IllegalArgumentException("toolCalls must not be null");
        if (turnUsage == null) throw new IllegalArgumentException("turnUsage must not be null");
        if (iterations < 0) throw new IllegalArgumentException("iterations must be >= 0");
        toolCalls = List.copyOf(toolCalls);
    }

    public record ToolCall(String id, String name, String input, String output, boolean isError) {
        public ToolCall {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
            if (input == null) throw new IllegalArgumentException("input must not be null");
            if (output == null) throw new IllegalArgumentException("output must not be null");
        }
    }
}
