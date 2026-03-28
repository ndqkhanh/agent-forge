package agentforge.agent;

import agentforge.common.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages the agent pool — registration, routing, and task execution.
 *
 * Routes tasks to agents based on their agentType. Provides a TaskExecutor
 * adapter for integration with the WorkflowEngine.
 */
public final class AgentRuntime {

    private static final Logger log = LoggerFactory.getLogger(AgentRuntime.class);

    private final Map<String, Agent> agents = new ConcurrentHashMap<>();

    /**
     * Register an agent in the pool.
     */
    public void registerAgent(Agent agent) {
        agents.put(agent.agentType(), agent);
        log.info("Registered agent: {}", agent.agentType());
    }

    /**
     * Check if an agent type is registered.
     */
    public boolean hasAgent(String agentType) {
        return agents.containsKey(agentType);
    }

    /**
     * Get all registered agent types.
     */
    public Set<String> registeredAgentTypes() {
        return Set.copyOf(agents.keySet());
    }

    /**
     * Get the number of registered agents.
     */
    public int agentCount() {
        return agents.size();
    }

    /**
     * Execute a task by routing to the appropriate agent.
     *
     * @param task   the task definition (contains agentType)
     * @param inputs predecessor outputs
     * @return a future completing with the task result
     */
    public CompletableFuture<TaskResult> executeTask(TaskDefinition task, Map<String, String> inputs) {
        Agent agent = agents.get(task.agentType());
        if (agent == null) {
            return CompletableFuture.completedFuture(
                    TaskResult.failed(task.id(),
                            "No agent registered for type: " + task.agentType(),
                            Duration.ZERO));
        }

        Instant start = Instant.now();
        return agent.execute(inputs)
                .thenApply(output -> {
                    Duration elapsed = Duration.between(start, Instant.now());
                    log.debug("Task {} completed by agent {} in {}ms",
                            task.id(), agent.agentType(), elapsed.toMillis());
                    return TaskResult.success(task.id(), output, elapsed);
                })
                .exceptionally(ex -> {
                    Duration elapsed = Duration.between(start, Instant.now());
                    log.error("Task {} failed: {}", task.id(), ex.getMessage());
                    return TaskResult.failed(task.id(), ex.getMessage(), elapsed);
                });
    }

    /**
     * Create a TaskExecutor adapter for use with WorkflowEngine.
     */
    public agentforge.common.model.TaskExecutor asTaskExecutor() {
        return this::executeTask;
    }
}
