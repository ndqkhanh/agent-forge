package com.agentforge.common.model;

public enum TaskStatus {
    PENDING,
    RUNNING,
    COMPLETED,
    FAILED,
    CANCELLED,
    SPECULATIVE,
    ROLLED_BACK;

    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED || this == CANCELLED || this == ROLLED_BACK;
    }

    public boolean isActive() {
        return this == RUNNING || this == SPECULATIVE;
    }
}
