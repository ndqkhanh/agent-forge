package agentforge.orchestrator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Coordinates rollback on speculation miss.
 *
 * Restores workflow state from a checkpoint and cleans up the used checkpoint.
 * Analogous to CPU pipeline flush on branch misprediction.
 */
public final class RollbackCoordinator {

    private static final Logger log = LoggerFactory.getLogger(RollbackCoordinator.class);

    private final CheckpointManager checkpointManager;
    private final AtomicLong rollbackCounter = new AtomicLong(0);

    public RollbackCoordinator(CheckpointManager checkpointManager) {
        this.checkpointManager = checkpointManager;
    }

    /**
     * Rollback to a checkpoint, restoring the saved state.
     * The checkpoint is deleted after restore.
     *
     * @param checkpointId the checkpoint to rollback to
     * @return the restored checkpoint data
     * @throws IllegalArgumentException if checkpoint not found
     */
    public Checkpoint rollback(String checkpointId) {
        Checkpoint checkpoint = checkpointManager.restore(checkpointId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Checkpoint not found: " + checkpointId));

        checkpointManager.delete(checkpointId);
        rollbackCounter.incrementAndGet();

        log.info("Rolled back to checkpoint {} for workflow {} ({} task results restored)",
                checkpointId, checkpoint.workflowId(), checkpoint.taskResults().size());

        return checkpoint;
    }

    /**
     * Total number of rollbacks performed.
     */
    public long rollbackCount() {
        return rollbackCounter.get();
    }
}
