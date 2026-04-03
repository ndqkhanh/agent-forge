package com.agentforge.runtime.compaction;

import com.agentforge.common.model.Session;

/**
 * A strategy for compacting a conversation session to reduce token usage.
 */
public interface CompactionStrategy {
    Session compact(Session session, int contextWindow);
    String name();

    /**
     * Relative cost of this strategy.
     * 0.0 = free (no LLM call), 1.0 = expensive (requires LLM API call).
     */
    double estimatedCost();
}
