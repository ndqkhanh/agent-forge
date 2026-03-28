package agentforge.orchestrator;

import agentforge.common.model.TaskId;
import agentforge.common.model.TaskResult;
import agentforge.common.model.WorkflowId;

import java.time.Instant;
import java.util.Map;

/**
 * A snapshot of workflow execution state at a point in time.
 * Used for rollback on speculation miss.
 *
 * @param id           unique checkpoint identifier
 * @param workflowId   the workflow this checkpoint belongs to
 * @param taskResults  snapshot of completed task results
 * @param createdAt    when the checkpoint was taken
 */
public record Checkpoint(
        String id,
        WorkflowId workflowId,
        Map<TaskId, TaskResult> taskResults,
        Instant createdAt) {

    public Checkpoint {
        taskResults = Map.copyOf(taskResults);
    }
}
