package agentforge.agent;

import java.util.List;
import java.util.Map;

/**
 * Transport abstraction for an MCP server.
 * Real implementation would use JSON-RPC 2.0 over HTTP/WebSocket.
 */
public interface MCPServer {

    /** List all tools available on this server. */
    List<MCPToolDefinition> listTools();

    /** Invoke a tool by name with the given parameters. */
    MCPToolResult invoke(String toolName, Map<String, Object> params);
}
