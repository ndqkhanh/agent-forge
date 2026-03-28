package agentforge.agent;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Hardcoded classifier agent — classifies input text into sentiment categories.
 * In production, this would call an LLM. For testing, it uses simple keyword matching.
 */
public final class ClassifierAgent implements Agent {

    @Override
    public String agentType() {
        return "classifier";
    }

    @Override
    public CompletableFuture<String> execute(Map<String, String> inputs) {
        return CompletableFuture.supplyAsync(() -> {
            String combined = String.join(" ", inputs.values()).toLowerCase();

            if (combined.isEmpty()) {
                return "Classification: NEUTRAL (no input)";
            }

            int positiveSignals = countOccurrences(combined, "amazing", "love", "great", "excellent", "good", "best");
            int negativeSignals = countOccurrences(combined, "terrible", "hate", "awful", "bad", "worst", "poor");

            String label;
            if (positiveSignals > negativeSignals) {
                label = "POSITIVE";
            } else if (negativeSignals > positiveSignals) {
                label = "NEGATIVE";
            } else {
                label = "NEUTRAL";
            }

            return "Classification: " + label + " (score: +" + positiveSignals + "/-" + negativeSignals + ")";
        });
    }

    private static int countOccurrences(String text, String... keywords) {
        int count = 0;
        for (String keyword : keywords) {
            if (text.contains(keyword)) count++;
        }
        return count;
    }
}
