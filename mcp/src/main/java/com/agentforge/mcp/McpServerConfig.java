package com.agentforge.mcp;

import java.util.List;
import java.util.Map;

/**
 * Configuration for a single MCP server connection.
 */
public record McpServerConfig(
        String name,
        String transportType,
        String command,
        List<String> args,
        String url,
        Map<String, String> env
) {
    public McpServerConfig {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (transportType == null || transportType.isBlank()) {
            throw new IllegalArgumentException("transportType must not be blank");
        }
        args = args != null ? List.copyOf(args) : List.of();
        env = env != null ? Map.copyOf(env) : Map.of();
    }
}
