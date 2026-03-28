package agentforge.orchestrator;

import agentforge.common.model.TaskId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Speculative execution engine — the core innovation of AgentForge.
 *
 * Analogous to CPU branch prediction: predicts task outputs, speculatively
 * executes dependent tasks with predicted inputs, and validates when the
 * actual result arrives. On hit: commit speculative work. On miss: rollback.
 *
 * Supports cascading speculation with confidence decay: each level of
 * cascade multiplies confidence by a decay factor, so deeper speculation
 * requires increasingly confident predictions.
 */
public final class SpeculationEngine {

    private static final Logger log = LoggerFactory.getLogger(SpeculationEngine.class);

    public enum ValidationResult {
        HIT,              // Prediction matched actual output
        MISS,             // Prediction did not match — rollback needed
        NO_SPECULATION    // No active speculation for this task
    }

    private final PredictionModel predictionModel;
    private final double confidenceThreshold;
    private final int maxDepth;
    private final double decayFactor;

    /** Active (unvalidated) speculations: stack of (taskId, predictedOutput). */
    private final Deque<SpeculationEntry> activeStack = new ConcurrentLinkedDeque<>();

    private final AtomicLong hitCount = new AtomicLong(0);
    private final AtomicLong missCount = new AtomicLong(0);
    private final AtomicLong totalCount = new AtomicLong(0);

    /**
     * @param predictionModel     the model used for predictions
     * @param confidenceThreshold minimum confidence to trigger speculation
     * @param maxDepth            maximum number of concurrent active speculations
     */
    public SpeculationEngine(PredictionModel predictionModel,
                             double confidenceThreshold, int maxDepth) {
        this(predictionModel, confidenceThreshold, maxDepth, 1.0);
    }

    /**
     * @param predictionModel     the model used for predictions
     * @param confidenceThreshold minimum confidence to trigger speculation
     * @param maxDepth            maximum cascade depth
     * @param decayFactor         confidence multiplier per cascade level (0.0-1.0)
     */
    public SpeculationEngine(PredictionModel predictionModel,
                             double confidenceThreshold, int maxDepth,
                             double decayFactor) {
        this.predictionModel = predictionModel;
        this.confidenceThreshold = confidenceThreshold;
        this.maxDepth = maxDepth;
        this.decayFactor = decayFactor;
    }

    /**
     * Attempt to speculate on a task's output.
     * Applies confidence decay based on current cascade depth.
     *
     * @param taskId the task to predict
     * @param input  the task's input
     * @return the prediction if effective confidence is above threshold, empty otherwise
     */
    public Optional<Prediction> speculate(TaskId taskId, String input) {
        int currentDepth = activeStack.size();

        // Check depth limit
        if (currentDepth >= maxDepth) {
            log.debug("Speculation depth limit reached ({}/{}), skipping {}",
                    currentDepth, maxDepth, taskId);
            return Optional.empty();
        }

        Prediction prediction = predictionModel.predict(taskId, input);

        // Apply cascade decay: effective confidence = raw confidence * decay^depth
        double effectiveConfidence = prediction.confidence() * Math.pow(decayFactor, currentDepth);

        if (effectiveConfidence < confidenceThreshold) {
            log.debug("Effective confidence {:.3f} (raw={:.3f}, decay^{}={:.3f}) below threshold {:.3f} for task {}",
                    effectiveConfidence, prediction.confidence(), currentDepth,
                    Math.pow(decayFactor, currentDepth), confidenceThreshold, taskId);
            return Optional.empty();
        }

        // Create a prediction with the effective (decayed) confidence for downstream use
        Prediction decayedPrediction = new Prediction(prediction.predictedOutput(), effectiveConfidence);

        activeStack.push(new SpeculationEntry(taskId, prediction.predictedOutput()));
        totalCount.incrementAndGet();
        log.info("Speculating on task {} (depth {}): predicted='{}' effective_confidence={:.3f}",
                taskId, currentDepth + 1, prediction.predictedOutput(), effectiveConfidence);

        return Optional.of(decayedPrediction);
    }

    /**
     * Validate a speculation against the actual output.
     * Removes the most recent speculation for matching task from the active stack.
     */
    public ValidationResult validate(TaskId taskId, String actualOutput) {
        Optional<SpeculationEntry> entry = removeSpeculation(taskId);

        if (entry.isEmpty()) {
            return ValidationResult.NO_SPECULATION;
        }

        // Record outcome for future learning
        predictionModel.recordOutcome(taskId, "", actualOutput);

        if (entry.get().predictedOutput().equals(actualOutput)) {
            hitCount.incrementAndGet();
            log.info("Speculation HIT for task {}: predicted={}", taskId, actualOutput);
            return ValidationResult.HIT;
        } else {
            missCount.incrementAndGet();
            log.warn("Speculation MISS for task {}: predicted={} actual={}",
                    taskId, entry.get().predictedOutput(), actualOutput);
            return ValidationResult.MISS;
        }
    }

    /**
     * Cascade rollback — invalidate all speculations from the given task onwards.
     * When a speculation misses, all deeper speculations that depended on it
     * are also invalid and must be rolled back.
     *
     * @param taskId       the task that missed
     * @param actualOutput the actual output
     * @return number of additional speculations invalidated (not counting the original)
     */
    public int cascadeRollback(TaskId taskId, String actualOutput) {
        if (activeStack.isEmpty()) return 0;

        // Find and remove the target speculation
        Optional<SpeculationEntry> target = removeSpeculation(taskId);
        if (target.isEmpty()) return 0;

        missCount.incrementAndGet();
        predictionModel.recordOutcome(taskId, "", actualOutput);

        // Invalidate all remaining (deeper) speculations
        int invalidated = activeStack.size();
        activeStack.clear();

        log.warn("Cascade rollback from task {}: invalidated {} deeper speculations",
                taskId, invalidated);
        return invalidated;
    }

    // ========== Statistics ==========

    public long hits() { return hitCount.get(); }
    public long misses() { return missCount.get(); }
    public long totalSpeculations() { return totalCount.get(); }
    public int activeSpeculations() { return activeStack.size(); }

    /** Current cascade depth (number of active unvalidated speculations). */
    public int currentCascadeDepth() {
        return activeStack.size();
    }

    public double hitRate() {
        long total = hitCount.get() + missCount.get();
        return total == 0 ? 0.0 : (double) hitCount.get() / total;
    }

    // ========== Internal ==========

    private Optional<SpeculationEntry> removeSpeculation(TaskId taskId) {
        Iterator<SpeculationEntry> it = activeStack.iterator();
        while (it.hasNext()) {
            SpeculationEntry entry = it.next();
            if (entry.taskId().equals(taskId)) {
                it.remove();
                return Optional.of(entry);
            }
        }
        return Optional.empty();
    }

    private record SpeculationEntry(TaskId taskId, String predictedOutput) {}
}
