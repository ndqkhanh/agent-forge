package agentforge.orchestrator;

/**
 * Result of a prediction — predicted output and confidence score.
 *
 * @param predictedOutput the predicted task output
 * @param confidence      confidence score in [0.0, 1.0]
 */
public record Prediction(String predictedOutput, double confidence) {

    public Prediction {
        if (confidence < 0.0 || confidence > 1.0) {
            throw new IllegalArgumentException("Confidence must be in [0.0, 1.0], got: " + confidence);
        }
    }

    /**
     * Whether this prediction is confident enough to trigger speculative execution.
     */
    public boolean shouldSpeculate(double threshold) {
        return confidence >= threshold;
    }
}
