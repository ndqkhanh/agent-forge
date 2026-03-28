package agentforge.agent;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * Hardcoded summarizer agent — combines inputs and produces a summary.
 * In production, this would call an LLM. For testing, it deterministically
 * concatenates inputs into a summary format.
 */
public final class SummarizerAgent implements Agent {

    @Override
    public String agentType() {
        return "summarizer";
    }

    @Override
    public CompletableFuture<String> execute(Map<String, String> inputs) {
        return CompletableFuture.supplyAsync(() -> {
            String combined = inputs.entrySet().stream()
                    .sorted(Map.Entry.comparingByKey())
                    .map(e -> "[" + e.getKey() + ": " + truncate(e.getValue(), 100) + "]")
                    .collect(Collectors.joining(" "));
            return "Summary: " + (combined.isEmpty() ? "No input provided." : combined);
        });
    }

    private static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }
}
