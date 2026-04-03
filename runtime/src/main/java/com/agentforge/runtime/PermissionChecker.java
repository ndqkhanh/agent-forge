package com.agentforge.runtime;

/**
 * Abstraction for checking tool execution permissions.
 * Will be replaced by the actual config module interface.
 */
public interface PermissionChecker {
    boolean isAllowed(String toolName, PermissionLevel required);

    enum PermissionLevel {
        READ_ONLY,
        WORKSPACE_WRITE,
        DANGER_FULL_ACCESS
    }
}
