package com.agentforge.common.error;

/**
 * Base exception for all AgentForge errors.
 * Sealed to allow only known subtypes. Thread-safe.
 */
public abstract sealed class AgentForgeException extends RuntimeException
    permits ApiException, ToolException, SessionException, ConfigException, HookException, McpException {

    protected AgentForgeException(String message) {
        super(message);
    }

    protected AgentForgeException(String message, Throwable cause) {
        super(message, cause);
    }
}
