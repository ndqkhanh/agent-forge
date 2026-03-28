package agentforge.orchestrator;

import agentforge.common.model.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD tests for cascading speculation with confidence decay.
 *
 * Tests cover:
 * - Confidence decays with each cascade level
 * - Decay factor is configurable
 * - Deep cascades eventually fall below threshold
 * - Cascade depth tracking
 * - Cascading hit/miss propagation
 * - Rollback cascades (miss at depth 1 invalidates depths 2, 3)
 */
class CascadingSpeculationTest {

    private PredictionModel model;
    private SpeculationEngine engine;

    @BeforeEach
    void setUp() {
        model = new PredictionModel();
        // threshold=0.5, maxDepth=5, decayFactor=0.8
        engine = new SpeculationEngine(model, 0.5, 5, 0.8);

        // Train model with high confidence for "classify" tasks
        for (int i = 0; i < 30; i++) {
            model.recordOutcome(TaskId.of("classify"), "input " + i, "POSITIVE");
        }
    }

    // ========== Confidence Decay ==========

    @Test
    @DisplayName("first-level speculation has full confidence")
    void firstLevelFullConfidence() {
        var result = engine.speculate(TaskId.of("classify"), "input");

        assertThat(result).isPresent();
        assertThat(result.get().confidence()).isGreaterThan(0.5);
    }

    @Test
    @DisplayName("cascaded speculation has reduced confidence via decay factor")
    void cascadedSpeculationHasReducedConfidence() {
        var level1 = engine.speculate(TaskId.of("classify"), "input-1");
        assertThat(level1).isPresent();
        double conf1 = level1.get().confidence();

        var level2 = engine.speculate(TaskId.of("classify"), "input-2");
        assertThat(level2).isPresent();
        double conf2 = level2.get().confidence();

        // Level 2 should have lower effective confidence due to decay
        assertThat(conf2).isLessThanOrEqualTo(conf1);
    }

    @Test
    @DisplayName("deep enough cascading eventually falls below threshold")
    void deepCascadeFallsBelowThreshold() {
        // With decay=0.8, after enough levels the effective confidence drops below 0.5
        int speculationsAccepted = 0;
        for (int i = 0; i < 10; i++) {
            var result = engine.speculate(TaskId.of("classify"), "input-" + i);
            if (result.isPresent()) {
                speculationsAccepted++;
            } else {
                break;
            }
        }

        // Should accept some but not all 10 (limited by decay or depth)
        assertThat(speculationsAccepted).isGreaterThan(1);
        assertThat(speculationsAccepted).isLessThan(10);
    }

    // ========== Cascade Depth Tracking ==========

    @Test
    @DisplayName("currentCascadeDepth tracks active speculation depth")
    void currentCascadeDepthTracks() {
        assertThat(engine.currentCascadeDepth()).isEqualTo(0);

        engine.speculate(TaskId.of("classify"), "in-1");
        assertThat(engine.currentCascadeDepth()).isEqualTo(1);

        engine.speculate(TaskId.of("classify"), "in-2");
        assertThat(engine.currentCascadeDepth()).isEqualTo(2);

        // Validate one — depth decreases
        engine.validate(TaskId.of("classify"), "POSITIVE");
        assertThat(engine.currentCascadeDepth()).isEqualTo(1);
    }

    // ========== Cascading Validation ==========

    @Test
    @DisplayName("validating all cascaded speculations reports hits correctly")
    void cascadedValidationReportsHits() {
        engine.speculate(TaskId.of("classify"), "in-1");
        engine.speculate(TaskId.of("classify"), "in-2");
        engine.speculate(TaskId.of("classify"), "in-3");

        // All three are correct
        assertThat(engine.validate(TaskId.of("classify"), "POSITIVE"))
                .isEqualTo(SpeculationEngine.ValidationResult.HIT);
        assertThat(engine.validate(TaskId.of("classify"), "POSITIVE"))
                .isEqualTo(SpeculationEngine.ValidationResult.HIT);
        assertThat(engine.validate(TaskId.of("classify"), "POSITIVE"))
                .isEqualTo(SpeculationEngine.ValidationResult.HIT);

        assertThat(engine.hits()).isEqualTo(3);
    }

    // ========== Cascading Rollback ==========

    @Test
    @DisplayName("miss at early level invalidates all deeper speculations")
    void missInvalidatesDeeperSpeculations() {
        engine.speculate(TaskId.of("classify"), "in-1");
        engine.speculate(TaskId.of("classify"), "in-2");
        engine.speculate(TaskId.of("classify"), "in-3");

        // Miss at the first speculation — should cascade-invalidate all
        int invalidated = engine.cascadeRollback(TaskId.of("classify"), "NEGATIVE");

        assertThat(invalidated).isGreaterThanOrEqualTo(2); // at least in-2 and in-3
        assertThat(engine.currentCascadeDepth()).isEqualTo(0);
        assertThat(engine.misses()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("cascadeRollback returns 0 when no active speculations")
    void cascadeRollbackReturnsZeroWhenEmpty() {
        int invalidated = engine.cascadeRollback(TaskId.of("unknown"), "output");
        assertThat(invalidated).isEqualTo(0);
    }

    // ========== Decay Factor Configuration ==========

    @Test
    @DisplayName("lower decay factor limits cascade depth more aggressively")
    void lowerDecayLimitsCascade() {
        // Very aggressive decay: 0.5
        var aggressiveEngine = new SpeculationEngine(model, 0.5, 10, 0.5);

        int accepted = 0;
        for (int i = 0; i < 10; i++) {
            if (aggressiveEngine.speculate(TaskId.of("classify"), "in-" + i).isPresent()) {
                accepted++;
            } else {
                break;
            }
        }

        // With 0.5 decay, should stop much earlier than with 0.8
        assertThat(accepted).isLessThanOrEqualTo(3);
    }
}
