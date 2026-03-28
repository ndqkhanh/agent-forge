package agentforge.common.model;

import java.time.Duration;

/**
 * Defines a single task within a workflow DAG.
 *
 * @param id        unique task identifier within the workflow
 * @param agentType the agent type that should execute this task
 * @param timeout   maximum execution time
 */
public record TaskDefinition(
        TaskId id,
        String agentType,
        Duration timeout) {

    public TaskDefinition(TaskId id, String agentType) {
        this(id, agentType, Duration.ofSeconds(30));
    }
}
