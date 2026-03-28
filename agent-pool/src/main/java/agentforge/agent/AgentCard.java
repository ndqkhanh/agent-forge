package agentforge.agent;

import java.util.List;

/**
 * A2A Agent Card — describes an agent's capabilities for discovery.
 * Other agents use this to determine if delegation is possible.
 *
 * @param agentId      unique agent identifier
 * @param name         human-readable name
 * @param description  what this agent does
 * @param capabilities list of capability tags (e.g., "summarization", "classification")
 * @param endpoint     the agent's A2A endpoint URL
 */
public record AgentCard(
        String agentId,
        String name,
        String description,
        List<String> capabilities,
        String endpoint) {

    public boolean hasCapability(String capability) {
        return capabilities.contains(capability);
    }
}
