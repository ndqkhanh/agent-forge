package com.agentforge.runtime.compaction;

import com.agentforge.common.model.Session;

import java.util.List;

/**
 * Orchestrates adaptive compaction of conversation sessions.
 * Decides when to compact and delegates to the AdaptiveSelector for strategy choice.
 */
public final class CompactionEngine {

    /**
     * Default threshold: compact when used tokens exceed 70% of context window.
     */
    public static final double DEFAULT_COMPACTION_THRESHOLD = 0.70;

    private final List<CompactionStrategy> strategies;
    private final AdaptiveSelector selector;
    private final double compactionThreshold;

    public CompactionEngine(List<CompactionStrategy> strategies, AdaptiveSelector selector, double compactionThreshold) {
        if (strategies == null || strategies.isEmpty()) throw new IllegalArgumentException("strategies must not be empty");
        if (selector == null) throw new IllegalArgumentException("selector must not be null");
        if (compactionThreshold <= 0 || compactionThreshold >= 1)
            throw new IllegalArgumentException("compactionThreshold must be in (0, 1)");
        this.strategies = List.copyOf(strategies);
        this.selector = selector;
        this.compactionThreshold = compactionThreshold;
    }

    /**
     * Returns true if the session should be compacted based on token usage vs context window.
     */
    public boolean shouldCompact(Session session, int contextWindow) {
        if (contextWindow <= 0 || session.messages().isEmpty()) return false;
        double pressure = selector.tokenPressure(session, contextWindow);
        return pressure >= compactionThreshold;
    }

    /**
     * Compact the session using the adaptively selected strategy.
     */
    public Session compact(Session session, int contextWindow) {
        CompactionStrategy strategy = selector.select(session, contextWindow);
        return strategy.compact(session, contextWindow);
    }

    /**
     * Factory method creating a CompactionEngine with all four default strategies.
     * LlmSummarizationStrategy is omitted since it requires an ApiClient;
     * callers requiring LLM summarization should use {@link #withLlmSummarization}.
     */
    public static CompactionEngine withDefaults() {
        EntropyCalculator entropyCalculator = new EntropyCalculator();
        SlidingWindowStrategy sliding = new SlidingWindowStrategy();
        PriorityRetentionStrategy priority = new PriorityRetentionStrategy();
        EntropyPruningStrategy entropy = new EntropyPruningStrategy(entropyCalculator);

        // No-op stub for LLM summarization: falls back to sliding window (no ApiClient required)
        CompactionStrategy noopSummarization = new CompactionStrategy() {
            @Override
            public Session compact(Session session, int contextWindow) {
                return sliding.compact(session, contextWindow);
            }
            @Override public String name() { return "llm-summarization-noop"; }
            @Override public double estimatedCost() { return 1.0; }
        };

        AdaptiveSelector selector = new AdaptiveSelector(sliding, priority, entropy, noopSummarization, entropyCalculator);
        return new CompactionEngine(List.of(sliding, priority, entropy, noopSummarization), selector, DEFAULT_COMPACTION_THRESHOLD);
    }

    /**
     * Factory method including a real LlmSummarizationStrategy.
     */
    public static CompactionEngine withLlmSummarization(LlmSummarizationStrategy llmStrategy) {
        EntropyCalculator entropyCalculator = new EntropyCalculator();
        SlidingWindowStrategy sliding = new SlidingWindowStrategy();
        PriorityRetentionStrategy priority = new PriorityRetentionStrategy();
        EntropyPruningStrategy entropy = new EntropyPruningStrategy(entropyCalculator);

        AdaptiveSelector selector = new AdaptiveSelector(sliding, priority, entropy, llmStrategy, entropyCalculator);
        return new CompactionEngine(List.of(sliding, priority, entropy, llmStrategy), selector, DEFAULT_COMPACTION_THRESHOLD);
    }
}
