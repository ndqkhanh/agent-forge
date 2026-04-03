package com.agentforge.runtime.compaction;

import com.agentforge.common.model.ConversationMessage;
import com.agentforge.common.model.Session;
import com.agentforge.common.model.TokenUsage;

import java.util.List;

/**
 * Cheapest compaction strategy — keeps the last N messages.
 * No LLM call required. Estimated cost: 0.0.
 */
public final class SlidingWindowStrategy implements CompactionStrategy {

    public static final int DEFAULT_WINDOW_SIZE = 20;

    private final int windowSize;

    public SlidingWindowStrategy(int windowSize) {
        if (windowSize <= 0) throw new IllegalArgumentException("windowSize must be > 0");
        this.windowSize = windowSize;
    }

    public SlidingWindowStrategy() {
        this(DEFAULT_WINDOW_SIZE);
    }

    @Override
    public Session compact(Session session, int contextWindow) {
        List<ConversationMessage> messages = session.messages();
        if (messages.size() <= windowSize) {
            return session;
        }

        List<ConversationMessage> kept = messages.subList(messages.size() - windowSize, messages.size());

        // Recompute total usage from retained messages
        TokenUsage totalUsage = TokenUsage.ZERO;
        for (ConversationMessage msg : kept) {
            totalUsage = totalUsage.add(msg.usage());
        }

        return new Session(session.version(), session.id(), kept, totalUsage);
    }

    @Override
    public String name() {
        return "sliding-window";
    }

    @Override
    public double estimatedCost() {
        return 0.0;
    }
}
