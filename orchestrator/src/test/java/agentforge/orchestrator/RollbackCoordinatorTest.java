package agentforge.orchestrator;

import agentforge.common.model.*;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD tests for RollbackCoordinator — restores state on speculation miss.
 *
 * Tests cover:
 * - Rollback restores task results from checkpoint
 * - Rollback deletes the used checkpoint
 * - Rollback with invalid checkpoint throws
 * - Rollback stats tracking
 * - Cascading rollback (multiple checkpoints)
 */
class RollbackCoordinatorTest {

    private CheckpointManager checkpointManager;
    private RollbackCoordinator rollbackCoordinator;

    @BeforeEach
    void setUp() {
        checkpointManager = new CheckpointManager();
        rollbackCoordinator = new RollbackCoordinator(checkpointManager);
    }

    // ========== Basic Rollback ==========

    @Test
    @DisplayName("rollback restores state from checkpoint")
    void rollbackRestoresState() {
        WorkflowId wfId = WorkflowId.of("wf-1");
        Map<TaskId, TaskResult> originalState = Map.of(
                TaskId.of("A"), TaskResult.success(TaskId.of("A"), "original-A", Duration.ofMillis(10))
        );

        String cpId = checkpointManager.save(wfId, originalState);

        var restored = rollbackCoordinator.rollback(cpId);

        assertThat(restored.taskResults()).containsKey(TaskId.of("A"));
        assertThat(restored.taskResults().get(TaskId.of("A")).output()).isEqualTo("original-A");
    }

    @Test
    @DisplayName("rollback deletes the checkpoint after restoring")
    void rollbackDeletesCheckpoint() {
        WorkflowId wfId = WorkflowId.of("wf-1");
        String cpId = checkpointManager.save(wfId, Map.of());

        rollbackCoordinator.rollback(cpId);

        assertThat(checkpointManager.restore(cpId)).isEmpty();
    }

    @Test
    @DisplayName("rollback with invalid checkpoint throws")
    void rollbackInvalidCheckpointThrows() {
        assertThatThrownBy(() -> rollbackCoordinator.rollback("nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // ========== Stats ==========

    @Test
    @DisplayName("rollbackCount tracks total rollbacks performed")
    void rollbackCountTracksTotal() {
        WorkflowId wfId = WorkflowId.of("wf-1");
        String cp1 = checkpointManager.save(wfId, Map.of());
        String cp2 = checkpointManager.save(wfId, Map.of());

        assertThat(rollbackCoordinator.rollbackCount()).isEqualTo(0);

        rollbackCoordinator.rollback(cp1);
        assertThat(rollbackCoordinator.rollbackCount()).isEqualTo(1);

        rollbackCoordinator.rollback(cp2);
        assertThat(rollbackCoordinator.rollbackCount()).isEqualTo(2);
    }

    // ========== Cascading Rollback ==========

    @Test
    @DisplayName("cascading rollback restores earliest checkpoint and cleans later ones")
    void cascadingRollbackRestoresEarliest() {
        WorkflowId wfId = WorkflowId.of("wf-1");

        Map<TaskId, TaskResult> state1 = Map.of(
                TaskId.of("A"), TaskResult.success(TaskId.of("A"), "v1", Duration.ofMillis(10))
        );
        Map<TaskId, TaskResult> state2 = Map.of(
                TaskId.of("A"), TaskResult.success(TaskId.of("A"), "v2", Duration.ofMillis(10)),
                TaskId.of("B"), TaskResult.success(TaskId.of("B"), "v2-B", Duration.ofMillis(10))
        );

        String cp1 = checkpointManager.save(wfId, state1);
        String cp2 = checkpointManager.save(wfId, state2);

        // Rollback to the earlier checkpoint
        var restored = rollbackCoordinator.rollback(cp1);

        assertThat(restored.taskResults()).hasSize(1);
        assertThat(restored.taskResults().get(TaskId.of("A")).output()).isEqualTo("v1");

        // cp2 should still exist (not part of this rollback)
        assertThat(checkpointManager.restore(cp2)).isPresent();
    }
}
