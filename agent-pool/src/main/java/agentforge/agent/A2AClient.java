package agentforge.agent;

import agentforge.common.model.TaskDefinition;
import agentforge.common.model.TaskId;
import agentforge.common.model.TaskResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A2A (Agent-to-Agent) client — enables agents to discover peer capabilities
 * and delegate sub-tasks to other agents.
 *
 * Implements the A2A protocol pattern:
 * 1. Discovery via Agent Cards (capability advertisement)
 * 2. Delegation via task submission to a specific agent
 * 3. Result retrieval with status tracking
 *
 * In production, this would use HTTP/SSE for cross-process delegation.
 * Phase 3 uses in-process routing via AgentRuntime for unit testing.
 */
public final class A2AClient {

    private static final Logger log = LoggerFactory.getLogger(A2AClient.class);

    private final AgentRuntime runtime;
    private final Map<String, AgentCard> agentCards = new ConcurrentHashMap<>();
    private final AtomicLong delegationCount = new AtomicLong(0);

    public A2AClient(AgentRuntime runtime) {
        this.runtime = runtime;
    }

    /**
     * Register an agent card for discovery.
     */
    public void registerCard(AgentCard card) {
        agentCards.put(card.agentId(), card);
        log.info("Registered A2A agent card: {} (capabilities: {})",
                card.agentId(), card.capabilities());
    }

    /**
     * Discover agents that have a specific capability.
     */
    public List<AgentCard> discoverAgents(String capability) {
        return agentCards.values().stream()
                .filter(card -> card.hasCapability(capability))
                .toList();
    }

    /**
     * Get all registered agent cards.
     */
    public List<AgentCard> allAgentCards() {
        return List.copyOf(agentCards.values());
    }

    /**
     * Delegate a task to a specific agent by ID.
     */
    public CompletableFuture<A2ATaskResult> delegate(String agentId, String taskDescription,
                                                      Map<String, String> inputs) {
        return CompletableFuture.supplyAsync(() -> {
            delegationCount.incrementAndGet();
            String taskId = "a2a-" + UUID.randomUUID().toString().substring(0, 8);

            AgentCard card = agentCards.get(agentId);
            if (card == null) {
                log.warn("A2A delegation failed: agent {} not found", agentId);
                return A2ATaskResult.failure(taskId, agentId, "Agent not found: " + agentId);
            }

            // Resolve agent type from card name to runtime agent type
            String agentType = resolveAgentType(card);
            if (!runtime.hasAgent(agentType)) {
                return A2ATaskResult.failure(taskId, agentId,
                        "No runtime agent for type: " + agentType);
            }

            try {
                TaskResult result = runtime.executeTask(
                        new TaskDefinition(TaskId.of(taskId), agentType), inputs)
                        .get(30, TimeUnit.SECONDS);

                if (result.isSuccess()) {
                    log.info("A2A delegation succeeded: {} → {}", agentId, taskId);
                    return A2ATaskResult.success(taskId, agentId, result.output());
                } else {
                    return A2ATaskResult.failure(taskId, agentId, result.error());
                }
            } catch (Exception e) {
                log.error("A2A delegation failed: {} → {}: {}", agentId, taskId, e.getMessage());
                return A2ATaskResult.failure(taskId, agentId, e.getMessage());
            }
        }, Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Delegate by capability — discovers a matching agent and delegates to it.
     */
    public CompletableFuture<A2ATaskResult> delegateByCapability(String capability,
                                                                  String taskDescription,
                                                                  Map<String, String> inputs) {
        List<AgentCard> candidates = discoverAgents(capability);
        if (candidates.isEmpty()) {
            delegationCount.incrementAndGet();
            String taskId = "a2a-" + UUID.randomUUID().toString().substring(0, 8);
            return CompletableFuture.completedFuture(
                    A2ATaskResult.failure(taskId, "unknown",
                            "No agent found with capability: " + capability));
        }
        // Pick first matching agent
        return delegate(candidates.getFirst().agentId(), taskDescription, inputs);
    }

    /**
     * Total number of delegation attempts.
     */
    public long totalDelegations() {
        return delegationCount.get();
    }

    private String resolveAgentType(AgentCard card) {
        // Map agent card to runtime agent type by matching name pattern
        String name = card.name().toLowerCase();
        if (name.contains("summariz")) return "summarizer";
        if (name.contains("classif")) return "classifier";
        // Fallback: use first capability as agent type
        return card.capabilities().isEmpty() ? card.agentId() : card.capabilities().getFirst();
    }
}
