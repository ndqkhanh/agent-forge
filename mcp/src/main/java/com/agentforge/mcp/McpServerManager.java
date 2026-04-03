package com.agentforge.mcp;

import com.agentforge.common.error.McpException;
import com.agentforge.mcp.discovery.McpTool;
import com.agentforge.mcp.discovery.McpToolDiscovery;
import com.agentforge.mcp.transport.HttpTransport;
import com.agentforge.mcp.transport.McpTransport;
import com.agentforge.mcp.transport.StdioTransport;
import com.agentforge.tools.Tool;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Manages multiple MCP server connections and aggregates their tools.
 */
public final class McpServerManager implements AutoCloseable {

    private final Map<String, McpTransport> transports;
    private final Map<String, List<McpTool>> toolsByServer;

    private McpServerManager(Map<String, McpTransport> transports,
                              Map<String, List<McpTool>> toolsByServer) {
        this.transports = Map.copyOf(transports);
        this.toolsByServer = Map.copyOf(toolsByServer);
    }

    /**
     * Bootstrap: connect to all configured servers and discover their tools.
     */
    public static McpServerManager bootstrap(List<McpServerConfig> configs) throws McpException {
        Map<String, McpTransport> transports = new HashMap<>();
        Map<String, List<McpTool>> toolsByServer = new HashMap<>();

        for (McpServerConfig config : configs) {
            McpTransport transport = createTransport(config);
            transport.connect();

            McpToolDiscovery discovery = new McpToolDiscovery(transport);
            List<com.agentforge.common.model.ToolSchema> schemas = discovery.discoverTools();

            List<McpTool> tools = new ArrayList<>();
            for (com.agentforge.common.model.ToolSchema schema : schemas) {
                tools.add(new McpTool(schema, transport, config.name()));
            }

            transports.put(config.name(), transport);
            toolsByServer.put(config.name(), List.copyOf(tools));
        }

        return new McpServerManager(transports, toolsByServer);
    }

    /**
     * Get all discovered MCP tools across all servers.
     */
    public List<Tool> allTools() {
        List<Tool> all = new ArrayList<>();
        for (List<McpTool> tools : toolsByServer.values()) {
            all.addAll(tools);
        }
        return List.copyOf(all);
    }

    /**
     * Get tools from a specific server by name.
     */
    public List<Tool> toolsForServer(String serverName) {
        List<McpTool> tools = toolsByServer.get(serverName);
        if (tools == null) {
            return List.of();
        }
        return List.copyOf(tools);
    }

    /**
     * List all connected server names.
     */
    public Set<String> serverNames() {
        return transports.keySet();
    }

    @Override
    public void close() {
        for (McpTransport transport : transports.values()) {
            transport.close();
        }
    }

    private static McpTransport createTransport(McpServerConfig config) throws McpException {
        return switch (config.transportType()) {
            case "stdio" -> new StdioTransport(
                    config.command(),
                    config.args(),
                    config.env());
            case "http" -> new HttpTransport(
                    config.url(),
                    config.env());
            default -> throw new McpException(
                    "Unknown transport type: " + config.transportType());
        };
    }
}
