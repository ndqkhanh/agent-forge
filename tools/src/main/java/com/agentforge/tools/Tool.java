package com.agentforge.tools;

import com.agentforge.common.model.ToolSchema;

/**
 * A single callable tool with a name, description, JSON schema, and execution logic.
 * Implementations must be thread-safe; execute() may be called concurrently.
 */
public interface Tool {

    /** Unique name used to invoke this tool. */
    String name();

    /** Human-readable description of what the tool does. */
    String description();

    /** JSON Schema string describing the tool's input parameters. */
    String inputSchema();

    /**
     * Execute the tool with the given JSON-encoded input.
     * Must never throw — all errors are returned as ToolResult.error().
     *
     * @param inputJson JSON string matching inputSchema()
     * @return result of the invocation
     */
    ToolExecutor.ToolResult execute(String inputJson);

    /**
     * Convert this tool to a ToolSchema suitable for LLM API calls.
     *
     * @return ToolSchema record
     */
    default ToolSchema toSchema() {
        return new ToolSchema(name(), description(), inputSchema());
    }
}
