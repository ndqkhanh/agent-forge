package com.agentforge.common.error;

public non-sealed class HookException extends AgentForgeException {

    public HookException(String message) {
        super(message);
    }

    public HookException(String message, Throwable cause) {
        super(message, cause);
    }
}
