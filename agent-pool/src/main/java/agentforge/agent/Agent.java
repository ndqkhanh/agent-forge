package agentforge.agent;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for an agent that can execute tasks.
 * Each agent has a type identifier and processes inputs to produce output.
 */
public interface Agent {

    /** Unique type identifier for this agent (e.g., "summarizer", "classifier"). */
    String agentType();

    /**
     * Execute a task with the given inputs.
     *
     * @param inputs map of predecessor task outputs
     * @return the agent's output string
     */
    CompletableFuture<String> execute(Map<String, String> inputs);
}
