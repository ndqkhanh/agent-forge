package com.agentforge.common.error;

public non-sealed class McpException extends AgentForgeException {

    public McpException(String message) {
        super(message);
    }

    public McpException(String message, Throwable cause) {
        super(message, cause);
    }
}
