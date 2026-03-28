package agentforge.agent;

import java.util.Map;

/**
 * Definition of an MCP tool — describes what the tool does and its input schema.
 *
 * @param name        unique tool name (e.g., "web_search", "database_query")
 * @param description human-readable description of what the tool does
 * @param inputSchema JSON Schema describing the expected input parameters
 */
public record MCPToolDefinition(
        String name,
        String description,
        Map<String, Object> inputSchema) {}
