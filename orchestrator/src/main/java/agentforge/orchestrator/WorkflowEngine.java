package agentforge.orchestrator;

import agentforge.common.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

/**
 * DAG-based workflow execution engine.
 *
 * Parses a WorkflowDefinition into a dependency graph, topologically sorts it,
 * and executes tasks with maximum parallelism — fan-out tasks run concurrently
 * via Virtual Threads, fan-in tasks wait for all predecessors to complete.
 *
 * Thread-safe: each execution creates its own state. The engine is stateless.
 */
public final class WorkflowEngine {

    private static final Logger log = LoggerFactory.getLogger(WorkflowEngine.class);

    private final TaskExecutor executor;

    public WorkflowEngine(TaskExecutor executor) {
        this.executor = executor;
    }

    /**
     * Execute a workflow DAG, returning results for all completed tasks.
     * Tasks are dispatched as soon as their predecessors complete.
     * Uses Virtual Threads for concurrent fan-out execution.
     */
    public CompletableFuture<Map<TaskId, TaskResult>> execute(WorkflowDefinition workflow) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                return executeSync(workflow);
            } catch (Exception e) {
                throw new CompletionException(e);
            }
        }, Executors.newVirtualThreadPerTaskExecutor());
    }

    private Map<TaskId, TaskResult> executeSync(WorkflowDefinition workflow)
            throws InterruptedException, ExecutionException {

        List<TaskId> sorted = topologicalSort(workflow);
        Map<TaskId, TaskResult> results = new ConcurrentHashMap<>();
        Map<TaskId, CompletableFuture<TaskResult>> futures = new ConcurrentHashMap<>();

        // Build adjacency: task → set of predecessors (UNCONDITIONAL only)
        Map<TaskId, Set<TaskId>> predecessors = new HashMap<>();
        for (TaskId taskId : sorted) {
            predecessors.put(taskId, workflow.predecessors(taskId));
        }

        // Process tasks in topological order, dispatching when all predecessors are done
        for (TaskId taskId : sorted) {
            Set<TaskId> preds = predecessors.get(taskId);

            // Wait for all predecessor futures to complete
            List<CompletableFuture<TaskResult>> predFutures = new ArrayList<>();
            for (TaskId pred : preds) {
                CompletableFuture<TaskResult> pf = futures.get(pred);
                if (pf != null) predFutures.add(pf);
            }

            if (!predFutures.isEmpty()) {
                CompletableFuture.allOf(predFutures.toArray(new CompletableFuture[0])).join();
            }

            // Check if any predecessor failed — skip this task if so
            boolean anyPredFailed = preds.stream()
                    .map(results::get)
                    .anyMatch(r -> r != null && !r.isSuccess());

            if (anyPredFailed) {
                log.info("Skipping task {} — predecessor failed", taskId);
                continue;
            }

            // Collect inputs from predecessors
            Map<String, String> inputs = new HashMap<>();
            for (TaskId pred : preds) {
                TaskResult predResult = results.get(pred);
                if (predResult != null && predResult.output() != null) {
                    inputs.put(pred.value(), predResult.output());
                }
            }

            // Dispatch task execution
            TaskDefinition taskDef = workflow.getTask(taskId)
                    .orElseThrow(() -> new IllegalStateException("Task not found: " + taskId));

            CompletableFuture<TaskResult> future = executor.execute(taskDef, inputs)
                    .thenApply(result -> {
                        results.put(taskId, result);
                        return result;
                    });

            futures.put(taskId, future);
        }

        // Wait for all dispatched tasks
        for (CompletableFuture<TaskResult> f : futures.values()) {
            f.join();
        }

        return results;
    }

    /**
     * Topological sort of workflow tasks using Kahn's algorithm.
     * Only considers UNCONDITIONAL edges for ordering.
     *
     * @throws IllegalArgumentException if the graph contains a cycle
     */
    public static List<TaskId> topologicalSort(WorkflowDefinition workflow) {
        // Build in-degree map (UNCONDITIONAL edges only)
        Map<TaskId, Integer> inDegree = new LinkedHashMap<>();
        Map<TaskId, List<TaskId>> adjacency = new HashMap<>();

        for (TaskDefinition task : workflow.tasks()) {
            inDegree.put(task.id(), 0);
            adjacency.put(task.id(), new ArrayList<>());
        }

        for (TaskEdge edge : workflow.edges()) {
            if (edge.type() == EdgeType.UNCONDITIONAL) {
                adjacency.get(edge.from()).add(edge.to());
                inDegree.merge(edge.to(), 1, Integer::sum);
            }
        }

        // Kahn's algorithm
        Queue<TaskId> queue = new ArrayDeque<>();
        for (var entry : inDegree.entrySet()) {
            if (entry.getValue() == 0) {
                queue.add(entry.getKey());
            }
        }

        List<TaskId> sorted = new ArrayList<>();
        while (!queue.isEmpty()) {
            TaskId current = queue.poll();
            sorted.add(current);

            for (TaskId neighbor : adjacency.get(current)) {
                int newDegree = inDegree.get(neighbor) - 1;
                inDegree.put(neighbor, newDegree);
                if (newDegree == 0) {
                    queue.add(neighbor);
                }
            }
        }

        if (sorted.size() != workflow.tasks().size()) {
            throw new IllegalArgumentException("Workflow DAG contains a cycle");
        }

        return sorted;
    }
}
