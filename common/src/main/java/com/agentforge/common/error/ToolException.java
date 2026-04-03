package com.agentforge.common.error;

public non-sealed class ToolException extends AgentForgeException {

    public ToolException(String message) {
        super(message);
    }

    public ToolException(String message, Throwable cause) {
        super(message, cause);
    }
}
