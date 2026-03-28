package agentforge.gateway;

import agentforge.common.model.*;
import agentforge.orchestrator.WorkflowEngine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Gateway router — handles workflow CRUD and execution triggers.
 *
 * This is the business logic layer that sits behind the HTTP routes.
 * In production, Javalin routes would delegate to this class.
 * Testing this directly avoids needing an HTTP server in unit tests.
 */
public final class GatewayRouter {

    private static final Logger log = LoggerFactory.getLogger(GatewayRouter.class);

    private final WorkflowEngine engine;
    private final Map<String, WorkflowEntry> workflows = new ConcurrentHashMap<>();

    public GatewayRouter(WorkflowEngine engine) {
        this.engine = engine;
    }

    /**
     * Submit a new workflow for later execution.
     */
    public WorkflowResponse submitWorkflow(WorkflowRequest request) {
        String wfId = WorkflowId.generate().value();

        try {
            WorkflowDefinition def = toDefinition(request);
            // Validate DAG (detect cycles)
            WorkflowEngine.topologicalSort(def);

            workflows.put(wfId, new WorkflowEntry(wfId, request.name(), def, WorkflowStatus.PENDING, Map.of(), null));
            log.info("Workflow submitted: {} ({})", wfId, request.name());
            return WorkflowResponse.accepted(wfId, request.name());
        } catch (IllegalArgumentException e) {
            log.warn("Workflow rejected: {}: {}", request.name(), e.getMessage());
            return WorkflowResponse.failed(wfId, request.name(), e.getMessage());
        }
    }

    /**
     * Execute a previously submitted workflow.
     */
    public CompletableFuture<WorkflowResponse> executeWorkflow(String workflowId) {
        WorkflowEntry entry = workflows.get(workflowId);
        if (entry == null) {
            return CompletableFuture.completedFuture(
                    WorkflowResponse.failed(workflowId, "unknown", "Workflow not found"));
        }

        workflows.put(workflowId, entry.withStatus(WorkflowStatus.RUNNING));

        return engine.execute(entry.definition()).thenApply(results -> {
            Map<String, String> resultMap = results.entrySet().stream()
                    .filter(e -> e.getValue().isSuccess())
                    .collect(Collectors.toMap(
                            e -> e.getKey().value(),
                            e -> e.getValue().output()));

            boolean anyFailed = results.values().stream().anyMatch(r -> !r.isSuccess());
            if (anyFailed) {
                String error = results.values().stream()
                        .filter(r -> !r.isSuccess())
                        .map(TaskResult::error)
                        .collect(Collectors.joining("; "));
                var resp = WorkflowResponse.failed(workflowId, entry.name(), error);
                workflows.put(workflowId, entry.withStatus(WorkflowStatus.FAILED));
                return resp;
            }

            var resp = WorkflowResponse.completed(workflowId, entry.name(), resultMap);
            workflows.put(workflowId, entry.withStatus(WorkflowStatus.COMPLETED).withResults(resultMap));
            log.info("Workflow completed: {} ({} tasks)", workflowId, results.size());
            return resp;
        });
    }

    /**
     * Get the current status of a workflow.
     */
    public Optional<WorkflowResponse> getWorkflow(String workflowId) {
        WorkflowEntry entry = workflows.get(workflowId);
        if (entry == null) return Optional.empty();
        return Optional.of(new WorkflowResponse(
                entry.id(), entry.name(), entry.status(), entry.results(), entry.error()));
    }

    /**
     * List all workflows.
     */
    public List<WorkflowResponse> listWorkflows() {
        return workflows.values().stream()
                .map(e -> new WorkflowResponse(e.id(), e.name(), e.status(), e.results(), e.error()))
                .toList();
    }

    /**
     * Cancel a workflow.
     */
    public boolean cancelWorkflow(String workflowId) {
        WorkflowEntry entry = workflows.get(workflowId);
        if (entry == null) return false;
        workflows.put(workflowId, entry.withStatus(WorkflowStatus.CANCELLED));
        log.info("Workflow cancelled: {}", workflowId);
        return true;
    }

    private WorkflowDefinition toDefinition(WorkflowRequest request) {
        List<TaskDefinition> tasks = request.tasks().stream()
                .map(t -> new TaskDefinition(TaskId.of(t.id()), t.agentType()))
                .toList();

        List<TaskEdge> edges = request.edges().stream()
                .map(e -> {
                    EdgeType type = switch (e.type().toLowerCase()) {
                        case "speculative" -> EdgeType.SPECULATIVE;
                        case "conditional" -> EdgeType.CONDITIONAL;
                        default -> EdgeType.UNCONDITIONAL;
                    };
                    return new TaskEdge(TaskId.of(e.from()), TaskId.of(e.to()), type);
                })
                .toList();

        return new WorkflowDefinition(request.name(), tasks, edges);
    }

    private record WorkflowEntry(
            String id, String name, WorkflowDefinition definition,
            WorkflowStatus status, Map<String, String> results, String error) {

        WorkflowEntry withStatus(WorkflowStatus newStatus) {
            return new WorkflowEntry(id, name, definition, newStatus, results, error);
        }

        WorkflowEntry withResults(Map<String, String> newResults) {
            return new WorkflowEntry(id, name, definition, status, newResults, error);
        }
    }
}
