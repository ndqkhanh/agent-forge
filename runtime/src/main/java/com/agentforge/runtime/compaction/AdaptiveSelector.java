package com.agentforge.runtime.compaction;

import com.agentforge.common.model.ContentBlock;
import com.agentforge.common.model.ConversationMessage;
import com.agentforge.common.model.Session;

/**
 * Selects the optimal compaction strategy based on three signals:
 * <ol>
 *   <li>Token pressure: usedTokens / contextWindow</li>
 *   <li>Conversation entropy: average Shannon entropy across messages</li>
 *   <li>Conversation shape: ratio of messages with tool results</li>
 * </ol>
 */
public final class AdaptiveSelector {

    private static final double LOW_PRESSURE_THRESHOLD = 0.70;
    private static final double HIGH_PRESSURE_THRESHOLD = 0.85;
    private static final double HIGH_ENTROPY_THRESHOLD = 0.55;
    private static final double HIGH_TOOL_RATIO_THRESHOLD = 0.50;
    private static final double LOW_TOOL_RATIO_THRESHOLD = 0.30;

    /** Rough estimate: ~4 characters per token. */
    private static final int CHARS_PER_TOKEN = 4;

    private final SlidingWindowStrategy slidingWindow;
    private final PriorityRetentionStrategy priorityRetention;
    private final EntropyPruningStrategy entropyPruning;
    private final CompactionStrategy llmSummarization;
    private final EntropyCalculator entropyCalculator;

    public AdaptiveSelector(
            SlidingWindowStrategy slidingWindow,
            PriorityRetentionStrategy priorityRetention,
            EntropyPruningStrategy entropyPruning,
            CompactionStrategy llmSummarization,
            EntropyCalculator entropyCalculator) {
        this.slidingWindow = slidingWindow;
        this.priorityRetention = priorityRetention;
        this.entropyPruning = entropyPruning;
        this.llmSummarization = llmSummarization;
        this.entropyCalculator = entropyCalculator;
    }

    /**
     * Select the optimal strategy for the given session and context window size.
     *
     * @param session the current session
     * @param contextWindow the model's context window in tokens
     * @return the chosen compaction strategy
     */
    public CompactionStrategy select(Session session, int contextWindow) {
        double pressure = tokenPressure(session, contextWindow);
        double entropy = entropyCalculator.sessionEntropy(session);
        double toolRatio = toolCallRatio(session);

        // High pressure — use LLM summarization for best quality
        if (pressure > HIGH_PRESSURE_THRESHOLD) {
            return llmSummarization;
        }

        // Low pressure — sliding window is cheapest
        if (pressure < LOW_PRESSURE_THRESHOLD) {
            return slidingWindow;
        }

        // Medium pressure — choose based on conversation shape and entropy
        if (toolRatio > HIGH_TOOL_RATIO_THRESHOLD) {
            // Coding/tool-heavy session — keep tool results via priority retention
            return priorityRetention;
        }

        if (entropy > HIGH_ENTROPY_THRESHOLD) {
            // High entropy — entropy pruning can identify low-info messages
            return entropyPruning;
        }

        if (toolRatio < LOW_TOOL_RATIO_THRESHOLD) {
            // Pure Q&A session with low entropy — sliding window is fine
            return slidingWindow;
        }

        // Default for medium pressure, medium entropy, medium tool ratio
        return priorityRetention;
    }

    /** Estimate token pressure as fraction of context window used. */
    double tokenPressure(Session session, int contextWindow) {
        if (contextWindow <= 0) return 1.0;
        int usedTokens = session.totalUsage().totalTokens();
        if (usedTokens == 0) {
            // Fall back to character-based estimate
            int charCount = session.messages().stream()
                .flatMap(m -> m.blocks().stream())
                .mapToInt(b -> switch (b) {
                    case ContentBlock.Text t -> t.text().length();
                    case ContentBlock.ToolUse u -> u.inputJson().length();
                    case ContentBlock.ToolResult r -> r.content().length();
                })
                .sum();
            usedTokens = charCount / CHARS_PER_TOKEN;
        }
        return Math.min(1.0, (double) usedTokens / contextWindow);
    }

    /** Calculate ratio of messages containing tool results to total messages. */
    double toolCallRatio(Session session) {
        if (session.messages().isEmpty()) return 0.0;
        long withToolResults = session.messages().stream()
            .filter(m -> m.blocks().stream().anyMatch(b -> b instanceof ContentBlock.ToolResult))
            .count();
        return (double) withToolResults / session.messages().size();
    }
}
