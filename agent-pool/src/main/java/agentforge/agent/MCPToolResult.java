package agentforge.agent;

/**
 * Result of an MCP tool invocation.
 *
 * @param toolName the tool that was invoked
 * @param success  whether the invocation succeeded
 * @param output   the tool's output (on success)
 * @param error    error message (on failure)
 */
public record MCPToolResult(
        String toolName,
        boolean success,
        String output,
        String error) {

    public static MCPToolResult success(String toolName, String output) {
        return new MCPToolResult(toolName, true, output, null);
    }

    public static MCPToolResult failure(String toolName, String error) {
        return new MCPToolResult(toolName, false, null, error);
    }
}
