package com.agentforge.common.error;

public enum ErrorCode {
    API_ERROR("api_error", "LLM API call failed"),
    TOOL_EXECUTION_FAILED("tool_execution_failed", "Tool execution failed"),
    CONFIG_ERROR("config_error", "Configuration error"),
    HOOK_FAILED("hook_failed", "Hook execution failed"),
    MCP_ERROR("mcp_error", "MCP protocol error"),
    SPECULATION_FAILED("speculation_failed", "Speculative execution failed"),
    SESSION_ERROR("session_error", "Session management error"),
    PERMISSION_DENIED("permission_denied", "Permission denied"),
    VALIDATION_ERROR("validation_error", "Input validation failed"),
    TIMEOUT("timeout", "Operation timed out"),
    INTERNAL("internal_error", "Internal error");

    private final String code;
    private final String defaultMessage;

    ErrorCode(String code, String defaultMessage) {
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

    public String code() { return code; }
    public String defaultMessage() { return defaultMessage; }
}
