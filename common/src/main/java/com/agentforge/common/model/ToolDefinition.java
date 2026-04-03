package com.agentforge.common.model;

import java.util.Map;
import java.util.Objects;

/**
 * Schema definition for a tool that agents can invoke via MCP.
 */
public record ToolDefinition(
    String name,
    String description,
    Map<String, Object> inputSchema
) {
    public ToolDefinition {
        Objects.requireNonNull(name, "name must not be null");
        Objects.requireNonNull(description, "description must not be null");
        inputSchema = inputSchema != null ? Map.copyOf(inputSchema) : Map.of();
    }
}
