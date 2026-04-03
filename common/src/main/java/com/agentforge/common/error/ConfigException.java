package com.agentforge.common.error;

public non-sealed class ConfigException extends AgentForgeException {

    public ConfigException(String message) {
        super(message);
    }

    public ConfigException(String message, Throwable cause) {
        super(message, cause);
    }
}
