package com.agentforge.common.model;

/**
 * Metadata about a specific LLM model.
 * Immutable record. Thread-safe.
 */
public record ModelInfo(
    String id,
    String displayName,
    String provider,
    int contextWindow,
    double inputCostPer1kTokens,
    double outputCostPer1kTokens
) {
    public ModelInfo {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (displayName == null || displayName.isBlank()) throw new IllegalArgumentException("displayName must not be blank");
        if (provider == null || provider.isBlank()) throw new IllegalArgumentException("provider must not be blank");
        if (contextWindow <= 0) throw new IllegalArgumentException("contextWindow must be > 0");
        if (inputCostPer1kTokens < 0) throw new IllegalArgumentException("inputCostPer1kTokens must be >= 0");
        if (outputCostPer1kTokens < 0) throw new IllegalArgumentException("outputCostPer1kTokens must be >= 0");
    }

    public double estimateCost(TokenUsage usage) {
        return (usage.inputTokens() * inputCostPer1kTokens / 1000.0)
             + (usage.outputTokens() * outputCostPer1kTokens / 1000.0);
    }
}
