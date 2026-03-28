package agentforge.orchestrator;

import agentforge.common.model.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD tests for PredictionModel — confidence scoring for speculative execution.
 *
 * Tests cover:
 * - Prediction produces output + confidence score
 * - Confidence in [0.0, 1.0] range
 * - Historical data improves confidence
 * - Unknown tasks get low confidence
 * - Classification tasks get high confidence
 * - Similar inputs produce similar predictions
 */
class PredictionModelTest {

    private PredictionModel model;

    @BeforeEach
    void setUp() {
        model = new PredictionModel();
    }

    // ========== Basic Prediction ==========

    @Test
    @DisplayName("predict returns a prediction with confidence score")
    void predictReturnsPredictionWithConfidence() {
        var prediction = model.predict(TaskId.of("classify"), "input text");

        assertThat(prediction).isNotNull();
        assertThat(prediction.predictedOutput()).isNotNull();
        assertThat(prediction.confidence()).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("confidence is bounded between 0.0 and 1.0")
    void confidenceBounded() {
        for (int i = 0; i < 100; i++) {
            var prediction = model.predict(TaskId.of("task-" + i), "input-" + i);
            assertThat(prediction.confidence()).isBetween(0.0, 1.0);
        }
    }

    // ========== Historical Learning ==========

    @Test
    @DisplayName("recording outcomes increases confidence for seen task patterns")
    void recordingOutcomesIncreasesConfidence() {
        TaskId taskId = TaskId.of("classifier");

        double initialConfidence = model.predict(taskId, "some input").confidence();

        // Record several correct outcomes
        for (int i = 0; i < 10; i++) {
            model.recordOutcome(taskId, "positive text " + i, "POSITIVE");
        }

        double learnedConfidence = model.predict(taskId, "positive text new").confidence();

        assertThat(learnedConfidence).isGreaterThan(initialConfidence);
    }

    @Test
    @DisplayName("unknown task with no history gets low confidence")
    void unknownTaskGetsLowConfidence() {
        var prediction = model.predict(TaskId.of("never-seen-before"), "random input");

        assertThat(prediction.confidence()).isLessThanOrEqualTo(0.5);
    }

    // ========== Prediction Accuracy ==========

    @Test
    @DisplayName("after learning, predictions match most common outcome")
    void predictionsMatchMostCommonOutcome() {
        TaskId taskId = TaskId.of("sentiment");

        // Train with mostly POSITIVE outcomes
        for (int i = 0; i < 8; i++) {
            model.recordOutcome(taskId, "good text " + i, "POSITIVE");
        }
        for (int i = 0; i < 2; i++) {
            model.recordOutcome(taskId, "bad text " + i, "NEGATIVE");
        }

        var prediction = model.predict(taskId, "good text new");
        assertThat(prediction.predictedOutput()).isEqualTo("POSITIVE");
    }

    // ========== Confidence Threshold ==========

    @Test
    @DisplayName("shouldSpeculate returns true when confidence exceeds threshold")
    void shouldSpeculateAboveThreshold() {
        TaskId taskId = TaskId.of("classify");
        for (int i = 0; i < 20; i++) {
            model.recordOutcome(taskId, "input " + i, "LABEL_A");
        }

        var prediction = model.predict(taskId, "input new");
        assertThat(prediction.shouldSpeculate(0.5)).isTrue();
    }

    @Test
    @DisplayName("shouldSpeculate returns false when confidence below threshold")
    void shouldSpeculateBelowThreshold() {
        var prediction = model.predict(TaskId.of("unknown"), "input");
        assertThat(prediction.shouldSpeculate(0.9)).isFalse();
    }

    // ========== Stats ==========

    @Test
    @DisplayName("recordedOutcomeCount tracks total outcomes")
    void recordedOutcomeCountTracksTotal() {
        assertThat(model.recordedOutcomeCount()).isEqualTo(0);

        model.recordOutcome(TaskId.of("a"), "in", "out");
        model.recordOutcome(TaskId.of("b"), "in", "out");

        assertThat(model.recordedOutcomeCount()).isEqualTo(2);
    }
}
