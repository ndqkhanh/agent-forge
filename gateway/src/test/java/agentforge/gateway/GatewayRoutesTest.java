package agentforge.gateway;

import agentforge.common.model.*;
import agentforge.orchestrator.WorkflowEngine;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD tests for GatewayRouter — REST workflow CRUD and execution.
 *
 * Tests the router logic directly (no HTTP server needed).
 * Tests cover:
 * - Submit workflow → accepted
 * - Get workflow status
 * - Execute workflow → completed with results
 * - Get nonexistent workflow → not found
 * - Invalid workflow (cycle) → rejected
 * - List all workflows
 * - Cancel workflow
 */
class GatewayRoutesTest {

    private GatewayRouter router;

    @BeforeEach
    void setUp() {
        TaskExecutor fakeExecutor = (task, inputs) -> CompletableFuture.supplyAsync(() ->
                TaskResult.success(task.id(), task.id().value() + "-output", Duration.ofMillis(10)));
        var engine = new WorkflowEngine(fakeExecutor);
        router = new GatewayRouter(engine);
    }

    // ========== Submit Workflow ==========

    @Test
    @DisplayName("submitWorkflow returns accepted response with workflow ID")
    void submitWorkflowReturnsAccepted() {
        var request = new WorkflowRequest("test-wf",
                List.of(new WorkflowRequest.TaskRequest("A", "agent"),
                        new WorkflowRequest.TaskRequest("B", "agent")),
                List.of(new WorkflowRequest.EdgeRequest("A", "B", "unconditional")));

        WorkflowResponse response = router.submitWorkflow(request);

        assertThat(response.workflowId()).isNotNull();
        assertThat(response.name()).isEqualTo("test-wf");
        assertThat(response.status()).isEqualTo(WorkflowStatus.PENDING);
    }

    @Test
    @DisplayName("submitWorkflow with cycle is rejected")
    void submitWorkflowWithCycleRejected() {
        var request = new WorkflowRequest("cyclic",
                List.of(new WorkflowRequest.TaskRequest("A", "agent"),
                        new WorkflowRequest.TaskRequest("B", "agent")),
                List.of(new WorkflowRequest.EdgeRequest("A", "B", "unconditional"),
                        new WorkflowRequest.EdgeRequest("B", "A", "unconditional")));

        WorkflowResponse response = router.submitWorkflow(request);

        assertThat(response.status()).isEqualTo(WorkflowStatus.FAILED);
        assertThat(response.error()).contains("cycle");
    }

    // ========== Execute Workflow ==========

    @Test
    @DisplayName("executeWorkflow runs and returns completed results")
    void executeWorkflowReturnsCompleted() throws Exception {
        var request = new WorkflowRequest("exec-wf",
                List.of(new WorkflowRequest.TaskRequest("A", "agent"),
                        new WorkflowRequest.TaskRequest("B", "agent")),
                List.of(new WorkflowRequest.EdgeRequest("A", "B", "unconditional")));

        router.submitWorkflow(request);
        String wfId = router.listWorkflows().getFirst().workflowId();

        WorkflowResponse result = router.executeWorkflow(wfId).get(5, TimeUnit.SECONDS);

        assertThat(result.status()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(result.results()).containsKeys("A", "B");
        assertThat(result.results().get("A")).isEqualTo("A-output");
    }

    // ========== Get Workflow Status ==========

    @Test
    @DisplayName("getWorkflow returns current status")
    void getWorkflowReturnsStatus() {
        var request = new WorkflowRequest("status-wf",
                List.of(new WorkflowRequest.TaskRequest("A", "agent")),
                List.of());

        router.submitWorkflow(request);
        String wfId = router.listWorkflows().getFirst().workflowId();

        Optional<WorkflowResponse> response = router.getWorkflow(wfId);
        assertThat(response).isPresent();
        assertThat(response.get().name()).isEqualTo("status-wf");
    }

    @Test
    @DisplayName("getWorkflow with unknown ID returns empty")
    void getWorkflowUnknownReturnsEmpty() {
        assertThat(router.getWorkflow("nonexistent")).isEmpty();
    }

    // ========== List Workflows ==========

    @Test
    @DisplayName("listWorkflows returns all submitted workflows")
    void listWorkflowsReturnsAll() {
        router.submitWorkflow(new WorkflowRequest("wf-1",
                List.of(new WorkflowRequest.TaskRequest("A", "agent")), List.of()));
        router.submitWorkflow(new WorkflowRequest("wf-2",
                List.of(new WorkflowRequest.TaskRequest("B", "agent")), List.of()));

        assertThat(router.listWorkflows()).hasSize(2);
    }

    // ========== Cancel Workflow ==========

    @Test
    @DisplayName("cancelWorkflow sets status to CANCELLED")
    void cancelWorkflowSetsCancelled() {
        router.submitWorkflow(new WorkflowRequest("cancel-wf",
                List.of(new WorkflowRequest.TaskRequest("A", "agent")), List.of()));
        String wfId = router.listWorkflows().getFirst().workflowId();

        boolean cancelled = router.cancelWorkflow(wfId);

        assertThat(cancelled).isTrue();
        assertThat(router.getWorkflow(wfId).get().status()).isEqualTo(WorkflowStatus.CANCELLED);
    }

    @Test
    @DisplayName("cancelWorkflow with unknown ID returns false")
    void cancelWorkflowUnknownReturnsFalse() {
        assertThat(router.cancelWorkflow("nonexistent")).isFalse();
    }
}
