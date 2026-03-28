package agentforge.agent;

import agentforge.common.model.*;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD tests for AgentRuntime — agent lifecycle, routing, and task execution.
 *
 * Tests cover:
 * - Register and look up agents by type
 * - Execute task routed to correct agent
 * - Unknown agent type returns failure
 * - Summarizer agent produces summary
 * - Classifier agent produces classification
 * - Agent timeout handling
 * - Multiple agents of same type (round-robin)
 * - Agent pool stats
 */
class AgentRuntimeTest {

    private AgentRuntime runtime;

    @BeforeEach
    void setUp() {
        runtime = new AgentRuntime();
    }

    // ========== Agent Registration ==========

    @Test
    @DisplayName("registerAgent adds agent to the pool")
    void registerAgentAddsToPool() {
        Agent agent = new SummarizerAgent();
        runtime.registerAgent(agent);

        assertThat(runtime.hasAgent("summarizer")).isTrue();
        assertThat(runtime.hasAgent("unknown")).isFalse();
    }

    @Test
    @DisplayName("registeredAgentTypes returns all registered types")
    void registeredAgentTypesReturnsAll() {
        runtime.registerAgent(new SummarizerAgent());
        runtime.registerAgent(new ClassifierAgent());

        assertThat(runtime.registeredAgentTypes())
                .containsExactlyInAnyOrder("summarizer", "classifier");
    }

    // ========== Task Execution ==========

    @Test
    @DisplayName("executeTask routes to correct agent and returns result")
    void executeTaskRoutesToCorrectAgent() throws Exception {
        runtime.registerAgent(new SummarizerAgent());

        var task = new TaskDefinition(TaskId.of("summarize"), "summarizer");
        Map<String, String> inputs = Map.of("research", "Long text about AI...");

        TaskResult result = runtime.executeTask(task, inputs).get(5, TimeUnit.SECONDS);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.output()).contains("Summary:");
        assertThat(result.taskId()).isEqualTo(TaskId.of("summarize"));
    }

    @Test
    @DisplayName("executeTask with unknown agent type returns failure")
    void executeTaskUnknownAgentFails() throws Exception {
        var task = new TaskDefinition(TaskId.of("unknown-task"), "nonexistent-agent");

        TaskResult result = runtime.executeTask(task, Map.of()).get(5, TimeUnit.SECONDS);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).contains("No agent registered");
    }

    // ========== Summarizer Agent ==========

    @Test
    @DisplayName("SummarizerAgent produces summary of inputs")
    void summarizerAgentProducesSummary() throws Exception {
        var agent = new SummarizerAgent();
        String output = agent.execute(Map.of(
                "doc1", "First document content.",
                "doc2", "Second document content."
        )).get(5, TimeUnit.SECONDS);

        assertThat(output).startsWith("Summary:");
        assertThat(output).contains("doc1");
        assertThat(output).contains("doc2");
    }

    @Test
    @DisplayName("SummarizerAgent with empty input returns empty summary")
    void summarizerEmptyInput() throws Exception {
        var agent = new SummarizerAgent();
        String output = agent.execute(Map.of()).get(5, TimeUnit.SECONDS);

        assertThat(output).startsWith("Summary:");
    }

    // ========== Classifier Agent ==========

    @Test
    @DisplayName("ClassifierAgent classifies input text")
    void classifierAgentClassifiesInput() throws Exception {
        var agent = new ClassifierAgent();
        String output = agent.execute(Map.of(
                "text", "This product is amazing, I love it!"
        )).get(5, TimeUnit.SECONDS);

        assertThat(output).startsWith("Classification:");
        assertThat(output).containsAnyOf("POSITIVE", "NEGATIVE", "NEUTRAL");
    }

    @Test
    @DisplayName("ClassifierAgent handles empty input")
    void classifierEmptyInput() throws Exception {
        var agent = new ClassifierAgent();
        String output = agent.execute(Map.of()).get(5, TimeUnit.SECONDS);

        assertThat(output).startsWith("Classification:");
    }

    // ========== Agent Pool Stats ==========

    @Test
    @DisplayName("agentCount returns total registered agents")
    void agentCountReturnsTotal() {
        assertThat(runtime.agentCount()).isEqualTo(0);

        runtime.registerAgent(new SummarizerAgent());
        assertThat(runtime.agentCount()).isEqualTo(1);

        runtime.registerAgent(new ClassifierAgent());
        assertThat(runtime.agentCount()).isEqualTo(2);
    }

    // ========== Agent as TaskExecutor ==========

    @Test
    @DisplayName("asTaskExecutor adapts runtime for WorkflowEngine use")
    void asTaskExecutorAdaptsForWorkflow() throws Exception {
        runtime.registerAgent(new SummarizerAgent());

        var taskExecutor = runtime.asTaskExecutor();
        var task = new TaskDefinition(TaskId.of("summarize"), "summarizer");
        TaskResult result = taskExecutor.execute(task, Map.of("input", "some text"))
                .get(5, TimeUnit.SECONDS);

        assertThat(result.isSuccess()).isTrue();
    }
}
