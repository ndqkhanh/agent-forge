package com.agentforge.common.error;

public non-sealed class SessionException extends AgentForgeException {

    public SessionException(String message) {
        super(message);
    }

    public SessionException(String message, Throwable cause) {
        super(message, cause);
    }
}
