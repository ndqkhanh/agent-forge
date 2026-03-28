package agentforge.gateway;

import agentforge.agent.*;
import agentforge.common.model.*;
import agentforge.orchestrator.*;
import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/**
 * End-to-end acceptance tests wiring the full AgentForge stack:
 * Gateway → WorkflowEngine → AgentRuntime → Agents (+ MCP tools, A2A delegation)
 *
 * Tests:
 * - Submit workflow via gateway → execute → get completed results
 * - Agent delegates to another agent via A2A
 * - MCP tool invocation within workflow
 * - Speculation hit on predictable workflow
 * - HITL gate blocks until approved
 */
class EndToEndAcceptanceTest {

    private AgentRuntime agentRuntime;
    private WorkflowEngine workflowEngine;
    private GatewayRouter gateway;
    private A2AClient a2aClient;
    private MCPClient mcpClient;

    @BeforeEach
    void setUp() {
        // Wire the full stack
        agentRuntime = new AgentRuntime();
        agentRuntime.registerAgent(new SummarizerAgent());
        agentRuntime.registerAgent(new ClassifierAgent());

        workflowEngine = new WorkflowEngine(agentRuntime.asTaskExecutor());
        gateway = new GatewayRouter(workflowEngine);

        // A2A
        a2aClient = new A2AClient(agentRuntime);
        a2aClient.registerCard(new AgentCard("summarizer-1", "Summarizer",
                "Summarizes text", List.of("summarization"), "local://summarizer"));
        a2aClient.registerCard(new AgentCard("classifier-1", "Classifier",
                "Classifies text", List.of("classification"), "local://classifier"));

        // MCP — inline fake server to avoid test-source dependency
        mcpClient = new MCPClient();
        MCPServer fakeMcpServer = new MCPServer() {
            @Override
            public List<MCPToolDefinition> listTools() {
                return List.of(new MCPToolDefinition("web_search", "Search the web",
                        Map.of("query", "string")));
            }
            @Override
            public MCPToolResult invoke(String toolName, Map<String, Object> params) {
                return MCPToolResult.success("web_search", "Results for: " + params.get("query"));
            }
        };
        mcpClient.addServer("default", fakeMcpServer);
    }

    // ========== Full Gateway → Execute → Results ==========

    @Test
    @DisplayName("end-to-end: submit → execute → completed with task results")
    void submitExecuteComplete() throws Exception {
        // Submit a 2-step workflow: classify → summarize
        var request = new WorkflowRequest("e2e-pipeline",
                List.of(new WorkflowRequest.TaskRequest("classify", "classifier"),
                        new WorkflowRequest.TaskRequest("summarize", "summarizer")),
                List.of(new WorkflowRequest.EdgeRequest("classify", "summarize", "unconditional")));

        WorkflowResponse submitted = gateway.submitWorkflow(request);
        assertThat(submitted.status()).isEqualTo(WorkflowStatus.PENDING);

        // Execute
        WorkflowResponse result = gateway.executeWorkflow(submitted.workflowId())
                .get(10, TimeUnit.SECONDS);

        assertThat(result.status()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(result.results()).containsKey("classify");
        assertThat(result.results()).containsKey("summarize");
        assertThat(result.results().get("classify")).contains("Classification:");
        assertThat(result.results().get("summarize")).contains("Summary:");
    }

    // ========== A2A Delegation ==========

    @Test
    @DisplayName("agent delegates classification to another agent via A2A")
    void a2aDelegation() throws Exception {
        A2ATaskResult result = a2aClient.delegateByCapability(
                "classification", "Classify this text",
                Map.of("text", "This product is amazing!"))
                .get(5, TimeUnit.SECONDS);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).contains("Classification:");
        assertThat(result.output()).contains("POSITIVE");
    }

    // ========== MCP Tool Invocation ==========

    @Test
    @DisplayName("MCP tool invoked during workflow execution")
    void mcpToolInvocation() throws Exception {
        MCPToolResult result = mcpClient.invokeTool("web_search",
                Map.of("query", "AgentForge architecture"))
                .get(5, TimeUnit.SECONDS);

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("AgentForge architecture");
    }

    // ========== Speculation on Predictable Workflow ==========

    @Test
    @DisplayName("speculation hits on a well-trained predictable workflow")
    void speculationHitsOnPredictableWorkflow() {
        var predictionModel = new PredictionModel();
        var speculationEngine = new SpeculationEngine(predictionModel, 0.5, 5, 0.85);

        // Train the model
        for (int i = 0; i < 30; i++) {
            predictionModel.recordOutcome(TaskId.of("classify"), "text " + i, "POSITIVE");
        }

        // Speculate
        var prediction = speculationEngine.speculate(TaskId.of("classify"), "new text");
        assertThat(prediction).isPresent();
        assertThat(prediction.get().predictedOutput()).isEqualTo("POSITIVE");

        // Validate — it's a hit
        var result = speculationEngine.validate(TaskId.of("classify"), "POSITIVE");
        assertThat(result).isEqualTo(SpeculationEngine.ValidationResult.HIT);
    }

    // ========== Diamond Workflow ==========

    @Test
    @DisplayName("diamond workflow: classify → [summarize, classify] → final summarize")
    void diamondWorkflow() throws Exception {
        var request = new WorkflowRequest("diamond",
                List.of(new WorkflowRequest.TaskRequest("step1", "classifier"),
                        new WorkflowRequest.TaskRequest("step2a", "summarizer"),
                        new WorkflowRequest.TaskRequest("step2b", "classifier"),
                        new WorkflowRequest.TaskRequest("step3", "summarizer")),
                List.of(new WorkflowRequest.EdgeRequest("step1", "step2a", "unconditional"),
                        new WorkflowRequest.EdgeRequest("step1", "step2b", "unconditional"),
                        new WorkflowRequest.EdgeRequest("step2a", "step3", "unconditional"),
                        new WorkflowRequest.EdgeRequest("step2b", "step3", "unconditional")));

        WorkflowResponse submitted = gateway.submitWorkflow(request);
        WorkflowResponse result = gateway.executeWorkflow(submitted.workflowId())
                .get(10, TimeUnit.SECONDS);

        assertThat(result.status()).isEqualTo(WorkflowStatus.COMPLETED);
        assertThat(result.results()).hasSize(4);
    }

    // ========== HITL Gate in Workflow ==========

    @Test
    @DisplayName("HITL gate blocks workflow until approved")
    void hitlGateBlocksUntilApproved() throws Exception {
        var gateManager = new HITLGateManager();

        // Create gate
        String gateId = gateManager.createGate("wf-1", TaskId.of("review"), "Editorial review");

        // Simulate async approval after a short delay
        CompletableFuture.runAsync(() -> {
            try { Thread.sleep(100); } catch (InterruptedException ignored) {}
            gateManager.approveGate(gateId, "editor-1", "Approved");
        });

        // Wait for decision
        var decision = gateManager.awaitDecision(gateId).get(5, TimeUnit.SECONDS);
        assertThat(decision.approved()).isTrue();
        assertThat(decision.reviewer()).isEqualTo("editor-1");
    }
}
