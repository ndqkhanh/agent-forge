package agentforge.orchestrator;

import agentforge.common.model.*;
import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD tests for CheckpointManager — state snapshots for speculation rollback.
 *
 * Tests cover:
 * - Save checkpoint captures current state
 * - Restore checkpoint returns saved state
 * - Multiple checkpoints per workflow
 * - Checkpoint not found returns empty
 * - Delete checkpoint removes it
 * - Checkpoint contains task results and metadata
 */
class CheckpointManagerTest {

    private CheckpointManager checkpointManager;

    @BeforeEach
    void setUp() {
        checkpointManager = new CheckpointManager();
    }

    // ========== Save & Restore ==========

    @Test
    @DisplayName("save and restore checkpoint round-trips correctly")
    void saveAndRestoreRoundTrips() {
        WorkflowId wfId = WorkflowId.of("wf-1");
        Map<TaskId, TaskResult> state = Map.of(
                TaskId.of("A"), TaskResult.success(TaskId.of("A"), "output-A", java.time.Duration.ofMillis(10))
        );

        String checkpointId = checkpointManager.save(wfId, state);
        assertThat(checkpointId).isNotNull();

        var restored = checkpointManager.restore(checkpointId);
        assertThat(restored).isPresent();
        assertThat(restored.get().taskResults()).containsKey(TaskId.of("A"));
        assertThat(restored.get().taskResults().get(TaskId.of("A")).output()).isEqualTo("output-A");
    }

    @Test
    @DisplayName("restore nonexistent checkpoint returns empty")
    void restoreNonexistentReturnsEmpty() {
        assertThat(checkpointManager.restore("nonexistent")).isEmpty();
    }

    // ========== Multiple Checkpoints ==========

    @Test
    @DisplayName("multiple checkpoints for same workflow are independent")
    void multipleCheckpointsIndependent() {
        WorkflowId wfId = WorkflowId.of("wf-1");

        Map<TaskId, TaskResult> state1 = Map.of(
                TaskId.of("A"), TaskResult.success(TaskId.of("A"), "v1", java.time.Duration.ofMillis(10))
        );
        Map<TaskId, TaskResult> state2 = Map.of(
                TaskId.of("A"), TaskResult.success(TaskId.of("A"), "v2", java.time.Duration.ofMillis(10)),
                TaskId.of("B"), TaskResult.success(TaskId.of("B"), "v2-B", java.time.Duration.ofMillis(10))
        );

        String cp1 = checkpointManager.save(wfId, state1);
        String cp2 = checkpointManager.save(wfId, state2);

        assertThat(cp1).isNotEqualTo(cp2);
        assertThat(checkpointManager.restore(cp1).get().taskResults()).hasSize(1);
        assertThat(checkpointManager.restore(cp2).get().taskResults()).hasSize(2);
    }

    // ========== Delete ==========

    @Test
    @DisplayName("delete removes checkpoint")
    void deleteRemovesCheckpoint() {
        WorkflowId wfId = WorkflowId.of("wf-1");
        String cpId = checkpointManager.save(wfId, Map.of());

        assertThat(checkpointManager.restore(cpId)).isPresent();

        checkpointManager.delete(cpId);

        assertThat(checkpointManager.restore(cpId)).isEmpty();
    }

    // ========== Listing ==========

    @Test
    @DisplayName("listCheckpoints returns all checkpoints for a workflow")
    void listCheckpointsForWorkflow() {
        WorkflowId wf1 = WorkflowId.of("wf-1");
        WorkflowId wf2 = WorkflowId.of("wf-2");

        checkpointManager.save(wf1, Map.of());
        checkpointManager.save(wf1, Map.of());
        checkpointManager.save(wf2, Map.of());

        assertThat(checkpointManager.listCheckpoints(wf1)).hasSize(2);
        assertThat(checkpointManager.listCheckpoints(wf2)).hasSize(1);
    }

    // ========== Stats ==========

    @Test
    @DisplayName("checkpointCount tracks total active checkpoints")
    void checkpointCountTracksTotal() {
        assertThat(checkpointManager.checkpointCount()).isEqualTo(0);

        checkpointManager.save(WorkflowId.of("wf-1"), Map.of());
        checkpointManager.save(WorkflowId.of("wf-2"), Map.of());
        assertThat(checkpointManager.checkpointCount()).isEqualTo(2);

        checkpointManager.delete(checkpointManager.listCheckpoints(WorkflowId.of("wf-1")).getFirst());
        assertThat(checkpointManager.checkpointCount()).isEqualTo(1);
    }
}
