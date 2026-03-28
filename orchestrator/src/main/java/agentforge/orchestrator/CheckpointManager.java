package agentforge.orchestrator;

import agentforge.common.model.TaskId;
import agentforge.common.model.TaskResult;
import agentforge.common.model.WorkflowId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages state checkpoints for speculative execution rollback.
 *
 * Phase 1: in-memory storage. Phase 2: Redis-backed via CheckpointStore.
 * Saves snapshots of workflow task results before speculation begins,
 * enabling restore on speculation miss.
 */
public final class CheckpointManager {

    private static final Logger log = LoggerFactory.getLogger(CheckpointManager.class);

    private final Map<String, Checkpoint> checkpoints = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(1);

    /**
     * Save a checkpoint of the current workflow state.
     *
     * @param workflowId  the workflow being checkpointed
     * @param taskResults current completed task results to snapshot
     * @return the checkpoint ID for later restore/delete
     */
    public String save(WorkflowId workflowId, Map<TaskId, TaskResult> taskResults) {
        String cpId = "cp-" + idCounter.getAndIncrement();
        Checkpoint checkpoint = new Checkpoint(cpId, workflowId, taskResults, Instant.now());
        checkpoints.put(cpId, checkpoint);
        log.info("Saved checkpoint {} for workflow {} ({} task results)",
                cpId, workflowId, taskResults.size());
        return cpId;
    }

    /**
     * Restore a checkpoint by ID.
     *
     * @return the checkpoint if found, empty otherwise
     */
    public Optional<Checkpoint> restore(String checkpointId) {
        return Optional.ofNullable(checkpoints.get(checkpointId));
    }

    /**
     * Delete a checkpoint.
     */
    public void delete(String checkpointId) {
        Checkpoint removed = checkpoints.remove(checkpointId);
        if (removed != null) {
            log.info("Deleted checkpoint {}", checkpointId);
        }
    }

    /**
     * List all checkpoint IDs for a workflow.
     */
    public List<String> listCheckpoints(WorkflowId workflowId) {
        return checkpoints.values().stream()
                .filter(cp -> cp.workflowId().equals(workflowId))
                .map(Checkpoint::id)
                .toList();
    }

    /**
     * Total number of active checkpoints.
     */
    public int checkpointCount() {
        return checkpoints.size();
    }
}
