package agentforge.orchestrator;

import agentforge.common.model.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD tests for SpeculationEngine — predict → speculate → validate/rollback.
 *
 * Tests cover:
 * - Speculate when confidence above threshold → returns speculative result
 * - Skip speculation when confidence below threshold
 * - Validate correct speculation → commit (no rollback)
 * - Validate incorrect speculation → rollback
 * - Speculation tracking (active count, hit/miss stats)
 * - Confidence threshold configuration
 * - Speculation depth limit
 */
class SpeculationEngineTest {

    private PredictionModel predictionModel;
    private SpeculationEngine engine;

    @BeforeEach
    void setUp() {
        predictionModel = new PredictionModel();
        engine = new SpeculationEngine(predictionModel, 0.6, 3);
    }

    // ========== Speculate Decision ==========

    @Test
    @DisplayName("speculate returns predicted output when confidence above threshold")
    void speculateReturnsWhenConfident() {
        TaskId taskId = TaskId.of("classify");
        // Train model to be confident
        for (int i = 0; i < 20; i++) {
            predictionModel.recordOutcome(taskId, "input " + i, "POSITIVE");
        }

        var result = engine.speculate(taskId, "new input");

        assertThat(result).isPresent();
        assertThat(result.get().predictedOutput()).isEqualTo("POSITIVE");
    }

    @Test
    @DisplayName("speculate returns empty when confidence below threshold")
    void speculateReturnsEmptyWhenNotConfident() {
        var result = engine.speculate(TaskId.of("unknown"), "input");

        assertThat(result).isEmpty();
    }

    // ========== Validation ==========

    @Test
    @DisplayName("validate with matching output returns HIT")
    void validateMatchingReturnsHit() {
        TaskId taskId = TaskId.of("classify");
        for (int i = 0; i < 20; i++) {
            predictionModel.recordOutcome(taskId, "in", "POSITIVE");
        }
        engine.speculate(taskId, "in"); // start speculation

        var result = engine.validate(taskId, "POSITIVE");

        assertThat(result).isEqualTo(SpeculationEngine.ValidationResult.HIT);
    }

    @Test
    @DisplayName("validate with different output returns MISS")
    void validateDifferentReturnsMiss() {
        TaskId taskId = TaskId.of("classify");
        for (int i = 0; i < 20; i++) {
            predictionModel.recordOutcome(taskId, "in", "POSITIVE");
        }
        engine.speculate(taskId, "in"); // predicted POSITIVE

        var result = engine.validate(taskId, "NEGATIVE");

        assertThat(result).isEqualTo(SpeculationEngine.ValidationResult.MISS);
    }

    @Test
    @DisplayName("validate without prior speculation returns NO_SPECULATION")
    void validateWithoutSpeculationReturnsNone() {
        var result = engine.validate(TaskId.of("unknown"), "output");

        assertThat(result).isEqualTo(SpeculationEngine.ValidationResult.NO_SPECULATION);
    }

    // ========== Hit/Miss Statistics ==========

    @Test
    @DisplayName("hit and miss counters track correctly")
    void hitMissCountersTrack() {
        TaskId taskId = TaskId.of("classify");
        for (int i = 0; i < 20; i++) {
            predictionModel.recordOutcome(taskId, "in", "POSITIVE");
        }

        // Speculation 1: hit
        engine.speculate(taskId, "in");
        engine.validate(taskId, "POSITIVE");

        // Speculation 2: miss
        engine.speculate(taskId, "in");
        engine.validate(taskId, "NEGATIVE");

        assertThat(engine.hits()).isEqualTo(1);
        assertThat(engine.misses()).isEqualTo(1);
        assertThat(engine.totalSpeculations()).isEqualTo(2);
    }

    @Test
    @DisplayName("hitRate returns correct ratio")
    void hitRateReturnsCorrectRatio() {
        TaskId taskId = TaskId.of("classify");
        for (int i = 0; i < 20; i++) {
            predictionModel.recordOutcome(taskId, "in", "POSITIVE");
        }

        // 3 hits, 1 miss
        for (int i = 0; i < 3; i++) {
            engine.speculate(taskId, "in");
            engine.validate(taskId, "POSITIVE");
        }
        engine.speculate(taskId, "in");
        engine.validate(taskId, "NEGATIVE");

        assertThat(engine.hitRate()).isCloseTo(0.75, within(0.01));
    }

    @Test
    @DisplayName("hitRate returns 0.0 when no speculations")
    void hitRateZeroWhenNoSpeculations() {
        assertThat(engine.hitRate()).isEqualTo(0.0);
    }

    // ========== Depth Limit ==========

    @Test
    @DisplayName("speculation respects max depth limit")
    void speculationRespectsMaxDepth() {
        TaskId taskId = TaskId.of("classify");
        for (int i = 0; i < 20; i++) {
            predictionModel.recordOutcome(taskId, "in", "POSITIVE");
        }

        // Engine configured with maxDepth=3
        // Active speculations without validation fill up the depth
        engine.speculate(taskId, "in1");
        engine.speculate(taskId, "in2");
        engine.speculate(taskId, "in3");

        // 4th should be rejected (depth exceeded)
        var result = engine.speculate(taskId, "in4");
        assertThat(result).isEmpty();
    }

    // ========== Active Speculation Count ==========

    @Test
    @DisplayName("activeSpeculations tracks unvalidated speculations")
    void activeSpeculationsTracksUnvalidated() {
        TaskId taskId = TaskId.of("classify");
        for (int i = 0; i < 20; i++) {
            predictionModel.recordOutcome(taskId, "in", "POSITIVE");
        }

        assertThat(engine.activeSpeculations()).isEqualTo(0);

        engine.speculate(taskId, "in1");
        assertThat(engine.activeSpeculations()).isEqualTo(1);

        engine.speculate(taskId, "in2");
        assertThat(engine.activeSpeculations()).isEqualTo(2);

        engine.validate(taskId, "POSITIVE");
        assertThat(engine.activeSpeculations()).isEqualTo(1);
    }
}
