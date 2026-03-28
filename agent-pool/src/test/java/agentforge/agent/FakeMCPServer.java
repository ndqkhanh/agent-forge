package agentforge.agent;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Fake MCP server for testing — registers tools and handlers in-memory.
 */
public final class FakeMCPServer implements MCPServer {

    private final List<MCPToolDefinition> tools = new ArrayList<>();
    private final Map<String, Function<Map<String, Object>, MCPToolResult>> handlers = new ConcurrentHashMap<>();

    public void registerTool(MCPToolDefinition tool) {
        tools.add(tool);
    }

    public void setHandler(String toolName, Function<Map<String, Object>, MCPToolResult> handler) {
        handlers.put(toolName, handler);
    }

    @Override
    public List<MCPToolDefinition> listTools() {
        return List.copyOf(tools);
    }

    @Override
    public MCPToolResult invoke(String toolName, Map<String, Object> params) {
        var handler = handlers.get(toolName);
        if (handler == null) {
            return MCPToolResult.failure(toolName, "No handler for tool: " + toolName);
        }
        return handler.apply(params);
    }
}
