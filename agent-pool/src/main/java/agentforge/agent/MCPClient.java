package agentforge.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP (Model Context Protocol) client — discovers tools from registered
 * MCP servers and invokes them via JSON-RPC 2.0 semantics.
 *
 * Supports multiple servers, each hosting different tools.
 * Routes tool invocations to the correct server automatically.
 */
public final class MCPClient {

    private static final Logger log = LoggerFactory.getLogger(MCPClient.class);

    /** Registered MCP servers: name → server transport. */
    private final Map<String, MCPServer> servers = new ConcurrentHashMap<>();

    /** Tool → server mapping, built during discovery. */
    private final Map<String, MCPServer> toolRouting = new ConcurrentHashMap<>();

    private final AtomicLong invocationCount = new AtomicLong(0);

    /**
     * Register an MCP server.
     */
    public void addServer(String name, MCPServer server) {
        servers.put(name, server);
        // Discover tools from this server and update routing
        for (MCPToolDefinition tool : server.listTools()) {
            toolRouting.put(tool.name(), server);
        }
        log.info("Added MCP server '{}' with {} tools", name, server.listTools().size());
    }

    /**
     * Discover all available tools across all registered servers.
     */
    public List<MCPToolDefinition> discoverTools() {
        List<MCPToolDefinition> allTools = new ArrayList<>();
        for (MCPServer server : servers.values()) {
            allTools.addAll(server.listTools());
        }
        return allTools;
    }

    /**
     * Invoke a tool by name with the given parameters.
     * Routes to the correct server automatically.
     */
    public CompletableFuture<MCPToolResult> invokeTool(String toolName, Map<String, Object> params) {
        return CompletableFuture.supplyAsync(() -> {
            invocationCount.incrementAndGet();

            MCPServer server = toolRouting.get(toolName);
            if (server == null) {
                log.warn("Tool not found: {}", toolName);
                return MCPToolResult.failure(toolName, "Tool not found: " + toolName);
            }

            try {
                MCPToolResult result = server.invoke(toolName, params);
                log.debug("Tool {} invoked: success={}", toolName, result.success());
                return result;
            } catch (Exception e) {
                log.error("Tool {} invocation failed: {}", toolName, e.getMessage());
                return MCPToolResult.failure(toolName, e.getMessage());
            }
        }, Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Total number of tool invocations made.
     */
    public long totalInvocations() {
        return invocationCount.get();
    }

    /**
     * Number of registered MCP servers.
     */
    public int serverCount() {
        return servers.size();
    }
}
