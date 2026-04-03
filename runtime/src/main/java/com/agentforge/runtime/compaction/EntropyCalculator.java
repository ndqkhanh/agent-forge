package com.agentforge.runtime.compaction;

import com.agentforge.common.model.ContentBlock;
import com.agentforge.common.model.ConversationMessage;
import com.agentforge.common.model.Session;

import java.util.HashMap;
import java.util.Map;

/**
 * Computes Shannon entropy for text to identify low-information messages.
 * Higher entropy = more unique information = higher value to retain.
 */
public final class EntropyCalculator {

    /**
     * Calculate normalized Shannon entropy of a text string.
     * Returns a value in [0.0, 1.0] where:
     *   0.0 = single repeated word (zero entropy)
     *   1.0 = uniform distribution over all words (maximum entropy)
     *
     * @param text the input text
     * @return normalized entropy in [0.0, 1.0], or 0.0 for empty/null text
     */
    public double entropy(String text) {
        if (text == null || text.isBlank()) return 0.0;

        String[] words = text.toLowerCase().split("\\s+");
        if (words.length == 0) return 0.0;
        if (words.length == 1) return 0.0;

        // Count word frequencies
        Map<String, Integer> freq = new HashMap<>();
        for (String word : words) {
            if (!word.isBlank()) {
                freq.merge(word, 1, Integer::sum);
            }
        }

        int vocabSize = freq.size();
        if (vocabSize <= 1) return 0.0;

        int total = words.length;
        double entropy = 0.0;
        for (int count : freq.values()) {
            double p = (double) count / total;
            entropy -= p * (Math.log(p) / Math.log(2));
        }

        // Normalize by log2(vocabSize) to get [0.0, 1.0]
        double maxEntropy = Math.log(vocabSize) / Math.log(2);
        if (maxEntropy == 0.0) return 0.0;
        return Math.min(1.0, entropy / maxEntropy);
    }

    /**
     * Calculate average entropy across all text content in a message.
     *
     * @param message the conversation message
     * @return normalized entropy in [0.0, 1.0]
     */
    public double messageEntropy(ConversationMessage message) {
        if (message == null) return 0.0;

        StringBuilder allText = new StringBuilder();
        for (ContentBlock block : message.blocks()) {
            switch (block) {
                case ContentBlock.Text t -> allText.append(t.text()).append(" ");
                case ContentBlock.ToolUse u -> allText.append(u.name()).append(" ").append(u.inputJson()).append(" ");
                case ContentBlock.ToolResult r -> allText.append(r.content()).append(" ");
            }
        }

        return entropy(allText.toString().trim());
    }

    /**
     * Calculate the average entropy across all messages in a session.
     *
     * @param session the conversation session
     * @return average normalized entropy in [0.0, 1.0]
     */
    public double sessionEntropy(Session session) {
        if (session == null || session.messages().isEmpty()) return 0.0;

        double total = 0.0;
        int count = 0;
        for (ConversationMessage msg : session.messages()) {
            total += messageEntropy(msg);
            count++;
        }

        return count == 0 ? 0.0 : total / count;
    }
}
