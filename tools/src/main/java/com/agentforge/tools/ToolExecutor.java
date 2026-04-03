package com.agentforge.tools;

/**
 * Executes tools by name with JSON input.
 * Implementations may delegate to a registry, a remote MCP server, or any plugin.
 */
public interface ToolExecutor {

    /**
     * Execute a tool by name, passing the raw JSON input string.
     *
     * @param toolName the registered name of the tool
     * @param inputJson JSON-encoded input matching the tool's schema
     * @return a ToolResult containing output or an error message
     */
    ToolResult execute(String toolName, String inputJson);

    /**
     * Returns true when this executor can handle the named tool.
     *
     * @param toolName the tool name to check
     * @return true if supported
     */
    boolean supports(String toolName);

    /**
     * Immutable result of a tool invocation.
     *
     * @param output  the tool output string (may be error text when isError is true)
     * @param isError true when the tool invocation failed
     */
    record ToolResult(String output, boolean isError) {

        public static ToolResult success(String output) {
            return new ToolResult(output, false);
        }

        public static ToolResult error(String message) {
            return new ToolResult(message, true);
        }
    }
}
