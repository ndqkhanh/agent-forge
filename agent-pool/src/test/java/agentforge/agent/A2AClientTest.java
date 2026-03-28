package agentforge.agent;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD tests for A2AClient — agent-to-agent delegation protocol.
 *
 * Tests cover:
 * - Register agent cards for discovery
 * - Discover agents by capability
 * - Delegate task to another agent
 * - Delegation failure handling
 * - Multiple agents with same capability (routing)
 * - Task status tracking
 * - No agent with required capability
 */
class A2AClientTest {

    private A2AClient a2aClient;
    private AgentRuntime runtime;

    @BeforeEach
    void setUp() {
        runtime = new AgentRuntime();
        runtime.registerAgent(new SummarizerAgent());
        runtime.registerAgent(new ClassifierAgent());

        a2aClient = new A2AClient(runtime);

        a2aClient.registerCard(new AgentCard(
                "summarizer-1", "Summarizer", "Summarizes text",
                List.of("summarization", "text-processing"),
                "local://summarizer"));
        a2aClient.registerCard(new AgentCard(
                "classifier-1", "Classifier", "Classifies text",
                List.of("classification", "sentiment-analysis"),
                "local://classifier"));
    }

    // ========== Discovery ==========

    @Test
    @DisplayName("discoverAgents returns agents with matching capability")
    void discoverByCapability() {
        List<AgentCard> agents = a2aClient.discoverAgents("summarization");

        assertThat(agents).hasSize(1);
        assertThat(agents.getFirst().agentId()).isEqualTo("summarizer-1");
    }

    @Test
    @DisplayName("discoverAgents returns empty for unknown capability")
    void discoverUnknownCapability() {
        assertThat(a2aClient.discoverAgents("code-execution")).isEmpty();
    }

    @Test
    @DisplayName("discoverAgents returns all registered cards when no filter")
    void discoverAllCards() {
        assertThat(a2aClient.allAgentCards()).hasSize(2);
    }

    // ========== Delegation ==========

    @Test
    @DisplayName("delegate sends task to agent and returns result")
    void delegateReturnsResult() throws Exception {
        A2ATaskResult result = a2aClient.delegate(
                "summarizer-1", "Summarize this text",
                Map.of("input", "Long document..."))
                .get(5, TimeUnit.SECONDS);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.agentId()).isEqualTo("summarizer-1");
        assertThat(result.output()).contains("Summary:");
    }

    @Test
    @DisplayName("delegate to unknown agent returns failure")
    void delegateUnknownAgentFails() throws Exception {
        A2ATaskResult result = a2aClient.delegate(
                "nonexistent", "Do something", Map.of())
                .get(5, TimeUnit.SECONDS);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).contains("not found");
    }

    @Test
    @DisplayName("delegateByCapability finds and delegates to matching agent")
    void delegateByCapability() throws Exception {
        A2ATaskResult result = a2aClient.delegateByCapability(
                "classification", "Classify this",
                Map.of("text", "Great product!"))
                .get(5, TimeUnit.SECONDS);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.agentId()).isEqualTo("classifier-1");
        assertThat(result.output()).contains("Classification:");
    }

    @Test
    @DisplayName("delegateByCapability with no matching agent returns failure")
    void delegateByCapabilityNoMatch() throws Exception {
        A2ATaskResult result = a2aClient.delegateByCapability(
                "code-execution", "Run code", Map.of())
                .get(5, TimeUnit.SECONDS);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.error()).contains("No agent");
    }

    // ========== Task Tracking ==========

    @Test
    @DisplayName("completed delegations are tracked")
    void completedDelegationsTracked() throws Exception {
        a2aClient.delegate("summarizer-1", "task 1", Map.of("input", "text"))
                .get(5, TimeUnit.SECONDS);
        a2aClient.delegate("classifier-1", "task 2", Map.of("text", "nice"))
                .get(5, TimeUnit.SECONDS);

        assertThat(a2aClient.totalDelegations()).isEqualTo(2);
    }

    // ========== Multiple Agents Same Capability ==========

    @Test
    @DisplayName("multiple agents with same capability are all discoverable")
    void multipleAgentsDiscoverable() {
        a2aClient.registerCard(new AgentCard(
                "summarizer-2", "Summarizer V2", "Advanced summarizer",
                List.of("summarization"), "local://summarizer-v2"));

        List<AgentCard> agents = a2aClient.discoverAgents("summarization");
        assertThat(agents).hasSize(2);
        assertThat(agents).extracting(AgentCard::agentId)
                .containsExactlyInAnyOrder("summarizer-1", "summarizer-2");
    }
}
