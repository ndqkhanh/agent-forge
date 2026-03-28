package agentforge.orchestrator;

import agentforge.common.model.TaskId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Statistical prediction model for speculative execution.
 *
 * Tracks historical outcomes per task type and uses frequency-based
 * prediction: the most common outcome for a task becomes the prediction,
 * with confidence proportional to its frequency.
 *
 * In production, this would be augmented with LLM-based prediction
 * (hybrid model). For Phase 1, pure statistical approach.
 */
public final class PredictionModel {

    private static final Logger log = LoggerFactory.getLogger(PredictionModel.class);
    private static final double BASE_CONFIDENCE = 0.3;

    /** Per-task outcome history: taskId → (output → count). */
    private final Map<TaskId, Map<String, Integer>> outcomeHistory = new ConcurrentHashMap<>();
    private final AtomicLong totalOutcomes = new AtomicLong(0);

    /**
     * Predict the output for a task given an input.
     * Uses historical frequency to determine most likely output and confidence.
     */
    public Prediction predict(TaskId taskId, String input) {
        Map<String, Integer> history = outcomeHistory.get(taskId);

        if (history == null || history.isEmpty()) {
            return new Prediction("unknown", BASE_CONFIDENCE);
        }

        // Find most frequent outcome
        int totalCount = history.values().stream().mapToInt(Integer::intValue).sum();
        var mostFrequent = history.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .orElseThrow();

        double frequency = (double) mostFrequent.getValue() / totalCount;
        // Confidence grows with sample size: asymptotic approach to frequency
        double sampleFactor = 1.0 - (1.0 / (1.0 + totalCount * 0.1));
        double confidence = Math.min(1.0, BASE_CONFIDENCE + (frequency - BASE_CONFIDENCE) * sampleFactor);

        return new Prediction(mostFrequent.getKey(), confidence);
    }

    /**
     * Record an actual outcome for future predictions.
     */
    public void recordOutcome(TaskId taskId, String input, String actualOutput) {
        outcomeHistory
                .computeIfAbsent(taskId, k -> new ConcurrentHashMap<>())
                .merge(actualOutput, 1, Integer::sum);
        totalOutcomes.incrementAndGet();
        log.debug("Recorded outcome for {}: {} (total: {})", taskId, actualOutput, totalOutcomes.get());
    }

    /**
     * Total number of recorded outcomes across all tasks.
     */
    public long recordedOutcomeCount() {
        return totalOutcomes.get();
    }
}
