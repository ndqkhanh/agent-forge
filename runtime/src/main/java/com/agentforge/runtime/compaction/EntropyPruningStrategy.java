package com.agentforge.runtime.compaction;

import com.agentforge.common.model.ConversationMessage;
import com.agentforge.common.model.Session;
import com.agentforge.common.model.TokenUsage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Drops low-information messages by Shannon entropy score.
 * High-entropy messages (unique information) are retained.
 * Low-entropy messages (repetitive, boilerplate) are pruned first.
 *
 * <p>No LLM call required. Estimated cost: 0.2 (entropy computation).
 */
public final class EntropyPruningStrategy implements CompactionStrategy {

    /** Target fraction of messages to retain after pruning. */
    private static final double RETAIN_FRACTION = 0.6;

    private final EntropyCalculator entropyCalculator;

    public EntropyPruningStrategy(EntropyCalculator entropyCalculator) {
        if (entropyCalculator == null) throw new IllegalArgumentException("entropyCalculator must not be null");
        this.entropyCalculator = entropyCalculator;
    }

    public EntropyPruningStrategy() {
        this(new EntropyCalculator());
    }

    @Override
    public Session compact(Session session, int contextWindow) {
        List<ConversationMessage> messages = session.messages();
        if (messages.size() <= 1) return session;

        int targetCount = Math.max(1, (int) (messages.size() * RETAIN_FRACTION));

        // Score each message with its original index
        record Scored(int index, ConversationMessage message, double entropy) {}
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            scored.add(new Scored(i, messages.get(i), entropyCalculator.messageEntropy(messages.get(i))));
        }

        // Sort by entropy descending — highest entropy (most information) first
        List<Scored> sorted = scored.stream()
            .sorted(Comparator.comparingDouble(Scored::entropy).reversed())
            .toList();

        // Keep the top targetCount by entropy, restore original order
        List<Integer> keptIndices = new ArrayList<>();
        for (int i = 0; i < Math.min(targetCount, sorted.size()); i++) {
            keptIndices.add(sorted.get(i).index());
        }
        keptIndices.sort(Comparator.naturalOrder());

        List<ConversationMessage> kept = keptIndices.stream()
            .map(messages::get)
            .toList();

        TokenUsage totalUsage = TokenUsage.ZERO;
        for (ConversationMessage msg : kept) {
            totalUsage = totalUsage.add(msg.usage());
        }

        return new Session(session.version(), session.id(), kept, totalUsage);
    }

    @Override
    public String name() {
        return "entropy-pruning";
    }

    @Override
    public double estimatedCost() {
        return 0.2;
    }
}
