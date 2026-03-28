package agentforge.common.model;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Abstraction for task execution. The WorkflowEngine delegates
 * actual execution to an implementation of this interface.
 * This allows testing the engine with fake executors.
 */
@FunctionalInterface
public interface TaskExecutor {

    /**
     * Execute a task with the given inputs from predecessor tasks.
     *
     * @param task   the task definition
     * @param inputs map of predecessor task ID → output string
     * @return a future that completes with the task result
     */
    CompletableFuture<TaskResult> execute(TaskDefinition task, Map<String, String> inputs);
}
