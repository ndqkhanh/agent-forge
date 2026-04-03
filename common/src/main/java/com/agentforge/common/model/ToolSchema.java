package com.agentforge.common.model;

/**
 * Schema definition for a tool that can be called by the LLM.
 * Immutable record. Thread-safe.
 */
public record ToolSchema(
    String name,
    String description,
    String inputSchemaJson
) {
    public ToolSchema {
        if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        if (description == null) throw new IllegalArgumentException("description must not be null");
        if (inputSchemaJson == null) throw new IllegalArgumentException("inputSchemaJson must not be null");
    }

    public static ToolSchema noParams(String name, String description) {
        return new ToolSchema(name, description, "{\"type\":\"object\",\"properties\":{}}");
    }
}
