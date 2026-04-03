package com.agentforge.config.permission;

public enum PermissionLevel {
    READ_ONLY(1),
    WORKSPACE_WRITE(2),
    DANGER_FULL_ACCESS(3);

    private final int level;

    PermissionLevel(int level) {
        this.level = level;
    }

    public boolean allows(PermissionLevel required) {
        return this.level >= required.level;
    }
}
