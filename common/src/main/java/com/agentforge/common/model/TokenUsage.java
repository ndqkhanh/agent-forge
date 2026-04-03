package com.agentforge.common.model;

/**
 * Token usage statistics for a single API call.
 * Immutable record. Thread-safe.
 */
public record TokenUsage(
    int inputTokens,
    int outputTokens,
    int cacheReadTokens,
    int cacheWriteTokens
) {
    public static final TokenUsage ZERO = new TokenUsage(0, 0, 0, 0);

    public TokenUsage {
        if (inputTokens < 0) throw new IllegalArgumentException("inputTokens must be >= 0");
        if (outputTokens < 0) throw new IllegalArgumentException("outputTokens must be >= 0");
        if (cacheReadTokens < 0) throw new IllegalArgumentException("cacheReadTokens must be >= 0");
        if (cacheWriteTokens < 0) throw new IllegalArgumentException("cacheWriteTokens must be >= 0");
    }

    public static TokenUsage of(int inputTokens, int outputTokens) {
        return new TokenUsage(inputTokens, outputTokens, 0, 0);
    }

    public int totalTokens() {
        return inputTokens + outputTokens;
    }

    public TokenUsage add(TokenUsage other) {
        return new TokenUsage(
            this.inputTokens + other.inputTokens,
            this.outputTokens + other.outputTokens,
            this.cacheReadTokens + other.cacheReadTokens,
            this.cacheWriteTokens + other.cacheWriteTokens
        );
    }
}
