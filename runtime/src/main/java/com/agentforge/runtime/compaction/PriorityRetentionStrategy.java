package com.agentforge.runtime.compaction;

import com.agentforge.common.model.ContentBlock;
import com.agentforge.common.model.ConversationMessage;
import com.agentforge.common.model.Session;
import com.agentforge.common.model.TokenUsage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Keeps high-value messages by priority score, respecting a token budget.
 *
 * <p>Priority scores:
 * <ul>
 *   <li>System messages: 1.0 (always keep)</li>
 *   <li>Tool results: 0.8 (high information density)</li>
 *   <li>User messages: 0.6</li>
 *   <li>Assistant text: 0.4</li>
 * </ul>
 *
 * <p>Estimated cost: 0.1 (no LLM, but scoring computation).
 */
public final class PriorityRetentionStrategy implements CompactionStrategy {

    /** Target fraction of context window to fill after compaction. */
    private static final double TARGET_FILL = 0.6;

    @Override
    public Session compact(Session session, int contextWindow) {
        List<ConversationMessage> messages = session.messages();
        if (messages.isEmpty()) return session;

        // Estimate token budget to keep (rough: 4 chars per token)
        int tokenBudget = (int) (contextWindow * TARGET_FILL);

        // Score each message with its original index for stable ordering
        record Scored(int index, ConversationMessage message, double priority) {}
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < messages.size(); i++) {
            scored.add(new Scored(i, messages.get(i), priority(messages.get(i))));
        }

        // Sort by priority descending, fill within token budget
        List<Scored> sorted = scored.stream()
            .sorted(Comparator.comparingDouble(Scored::priority).reversed())
            .toList();

        List<Integer> keptIndices = new ArrayList<>();
        int usedTokens = 0;
        for (Scored s : sorted) {
            int msgTokens = estimateTokens(s.message());
            if (s.priority() >= 1.0 || usedTokens + msgTokens <= tokenBudget) {
                keptIndices.add(s.index());
                usedTokens += msgTokens;
            }
        }

        // Restore original chronological order
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

    private double priority(ConversationMessage message) {
        if ("system".equals(message.role())) return 1.0;

        // Check if message contains tool results (user role with ToolResult blocks)
        boolean hasToolResults = message.blocks().stream()
            .anyMatch(b -> b instanceof ContentBlock.ToolResult);
        if (hasToolResults) return 0.8;

        return switch (message.role()) {
            case "user" -> 0.6;
            case "assistant" -> 0.4;
            default -> 0.5;
        };
    }

    /** Rough token estimate: ~4 characters per token. */
    private int estimateTokens(ConversationMessage message) {
        int charCount = 0;
        for (ContentBlock block : message.blocks()) {
            charCount += switch (block) {
                case ContentBlock.Text t -> t.text().length();
                case ContentBlock.ToolUse u -> u.inputJson().length() + u.name().length();
                case ContentBlock.ToolResult r -> r.content().length();
            };
        }
        return Math.max(1, charCount / 4);
    }

    @Override
    public String name() {
        return "priority-retention";
    }

    @Override
    public double estimatedCost() {
        return 0.1;
    }
}
