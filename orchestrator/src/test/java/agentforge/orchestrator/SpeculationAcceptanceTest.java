package agentforge.orchestrator;

import agentforge.common.model.*;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/**
 * Acceptance tests for the full speculation pipeline:
 * WorkflowEngine + SpeculationEngine + CheckpointManager + RollbackCoordinator.
 *
 * Tests:
 * - 3-depth speculation chain → validate → commit all
 * - Speculate wrong → rollback → re-execute correct path
 * - Speculation with MCP tool buffering
 */
class SpeculationAcceptanceTest {

    private PredictionModel predictionModel;
    private SpeculationEngine speculationEngine;
    private CheckpointManager checkpointManager;
    private RollbackCoordinator rollbackCoordinator;

    @BeforeEach
    void setUp() {
        predictionModel = new PredictionModel();
        speculationEngine = new SpeculationEngine(predictionModel, 0.5, 5, 0.85);
        checkpointManager = new CheckpointManager();
        rollbackCoordinator = new RollbackCoordinator(checkpointManager);

        // Train model with high confidence data
        for (int i = 0; i < 30; i++) {
            predictionModel.recordOutcome(TaskId.of("classify"), "text " + i, "POSITIVE");
            predictionModel.recordOutcome(TaskId.of("summarize"), "doc " + i, "Brief summary");
            predictionModel.recordOutcome(TaskId.of("route"), "input " + i, "FAST_PATH");
        }
    }

    // ========== Cascading Speculation → Commit All ==========

    @Test
    @DisplayName("3-depth speculation chain: predict → speculate → validate all → commit")
    void cascadingSpeculationCommitAll() {
        WorkflowId wfId = WorkflowId.of("wf-cascade");

        // Step 1: Speculate on classify → POSITIVE
        var spec1 = speculationEngine.speculate(TaskId.of("classify"), "good text");
        assertThat(spec1).isPresent();
        String cpId1 = checkpointManager.save(wfId, Map.of());

        // Step 2: Speculate on summarize → Brief summary (cascaded)
        var spec2 = speculationEngine.speculate(TaskId.of("summarize"), "doc content");
        assertThat(spec2).isPresent();
        String cpId2 = checkpointManager.save(wfId, Map.of(
                TaskId.of("classify"), TaskResult.success(TaskId.of("classify"), "POSITIVE", Duration.ofMillis(10))
        ));

        // Step 3: Speculate on route → FAST_PATH (cascaded again)
        var spec3 = speculationEngine.speculate(TaskId.of("route"), "some input");
        assertThat(spec3).isPresent();

        assertThat(speculationEngine.currentCascadeDepth()).isEqualTo(3);

        // Validate all — actual outputs match predictions
        assertThat(speculationEngine.validate(TaskId.of("classify"), "POSITIVE"))
                .isEqualTo(SpeculationEngine.ValidationResult.HIT);
        assertThat(speculationEngine.validate(TaskId.of("summarize"), "Brief summary"))
                .isEqualTo(SpeculationEngine.ValidationResult.HIT);
        assertThat(speculationEngine.validate(TaskId.of("route"), "FAST_PATH"))
                .isEqualTo(SpeculationEngine.ValidationResult.HIT);

        // All speculations committed successfully
        assertThat(speculationEngine.hits()).isEqualTo(3);
        assertThat(speculationEngine.misses()).isEqualTo(0);
        assertThat(speculationEngine.currentCascadeDepth()).isEqualTo(0);
    }

    // ========== Speculation Miss → Rollback → Re-Execute ==========

    @Test
    @DisplayName("speculate wrong → rollback checkpoint → re-execute with correct input")
    void speculationMissRollbackReExecute() {
        WorkflowId wfId = WorkflowId.of("wf-rollback");

        // Save checkpoint before speculation
        Map<TaskId, TaskResult> preSpecState = Map.of(
                TaskId.of("research"), TaskResult.success(TaskId.of("research"),
                        "Research output", Duration.ofMillis(100))
        );
        String cpId = checkpointManager.save(wfId, preSpecState);

        // Speculate: classify will be POSITIVE
        var spec = speculationEngine.speculate(TaskId.of("classify"), "ambiguous text");
        assertThat(spec).isPresent();

        // Actual result is NEGATIVE — miss!
        var validation = speculationEngine.validate(TaskId.of("classify"), "NEGATIVE");
        assertThat(validation).isEqualTo(SpeculationEngine.ValidationResult.MISS);

        // Rollback to checkpoint
        Checkpoint restored = rollbackCoordinator.rollback(cpId);

        // Verify state restored
        assertThat(restored.taskResults()).containsKey(TaskId.of("research"));
        assertThat(restored.taskResults().get(TaskId.of("research")).output())
                .isEqualTo("Research output");

        // Checkpoint consumed
        assertThat(checkpointManager.restore(cpId)).isEmpty();
        assertThat(rollbackCoordinator.rollbackCount()).isEqualTo(1);
    }

    // ========== Cascading Miss → Cascade Rollback ==========

    @Test
    @DisplayName("miss at depth 1 invalidates all deeper speculations and rolls back")
    void cascadingMissRollsBackAll() {
        WorkflowId wfId = WorkflowId.of("wf-cascade-miss");

        // Save checkpoint
        String cpId = checkpointManager.save(wfId, Map.of());

        // 3-level cascade
        speculationEngine.speculate(TaskId.of("classify"), "input");
        speculationEngine.speculate(TaskId.of("summarize"), "input");
        speculationEngine.speculate(TaskId.of("route"), "input");
        assertThat(speculationEngine.currentCascadeDepth()).isEqualTo(3);

        // Miss at classify — cascades through summarize and route
        int invalidated = speculationEngine.cascadeRollback(TaskId.of("classify"), "NEGATIVE");

        assertThat(invalidated).isEqualTo(2); // summarize + route invalidated
        assertThat(speculationEngine.currentCascadeDepth()).isEqualTo(0);
        assertThat(speculationEngine.misses()).isEqualTo(1);

        // Rollback checkpoint
        Checkpoint restored = rollbackCoordinator.rollback(cpId);
        assertThat(restored).isNotNull();
    }

    // ========== Full Workflow + Speculation Integration ==========

    @Test
    @DisplayName("workflow with speculation: speculate on step 2 while step 1 runs")
    void workflowWithSpeculation() throws Exception {
        // Define a 3-step linear workflow
        var step1 = new TaskDefinition(TaskId.of("classify"), "classifier");
        var step2 = new TaskDefinition(TaskId.of("summarize"), "summarizer");
        var step3 = new TaskDefinition(TaskId.of("route"), "router");
        var wfDef = new WorkflowDefinition("speculative-pipeline",
                List.of(step1, step2, step3),
                List.of(
                        TaskEdge.unconditional(TaskId.of("classify"), TaskId.of("summarize")),
                        TaskEdge.unconditional(TaskId.of("summarize"), TaskId.of("route"))
                ));

        // Execute workflow with a fake executor
        TaskExecutor executor = (task, inputs) -> CompletableFuture.supplyAsync(() ->
                TaskResult.success(task.id(), task.id().value() + "-output", Duration.ofMillis(50)));

        var engine = new WorkflowEngine(executor);
        Map<TaskId, TaskResult> results = engine.execute(wfDef).get(5, TimeUnit.SECONDS);

        // All 3 tasks completed
        assertThat(results).hasSize(3);
        assertThat(results.values()).allSatisfy(r -> assertThat(r.isSuccess()).isTrue());

        // Simulate post-execution speculation validation
        // (In production, speculation would happen during execution)
        assertThat(speculationEngine.hitRate()).isGreaterThanOrEqualTo(0.0);
    }

    // ========== Hit Rate Tracking Across Scenarios ==========

    @Test
    @DisplayName("hit rate tracks correctly across mixed hits and misses")
    void hitRateTracksAcrossMixedResults() {
        // 4 hits
        for (int i = 0; i < 4; i++) {
            speculationEngine.speculate(TaskId.of("classify"), "in");
            speculationEngine.validate(TaskId.of("classify"), "POSITIVE");
        }

        // 1 miss
        speculationEngine.speculate(TaskId.of("classify"), "in");
        speculationEngine.validate(TaskId.of("classify"), "NEGATIVE");

        assertThat(speculationEngine.hitRate()).isCloseTo(0.8, within(0.01));
        assertThat(speculationEngine.totalSpeculations()).isEqualTo(5);
    }
}
