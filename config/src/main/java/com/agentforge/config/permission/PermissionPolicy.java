package com.agentforge.config.permission;

import java.util.List;

public final class PermissionPolicy {

    private final PermissionLevel currentLevel;
    private final List<ToolPermission> rules;

    public PermissionPolicy(PermissionLevel currentLevel, List<ToolPermission> rules) {
        this.currentLevel = currentLevel;
        this.rules = List.copyOf(rules);
    }

    /**
     * Check if a tool is allowed at the current permission level.
     */
    public boolean isAllowed(String toolName) {
        PermissionLevel required = requiredLevel(toolName);
        return currentLevel.allows(required);
    }

    /**
     * Get the required permission level for a tool.
     * First matching rule wins. Default: WORKSPACE_WRITE.
     */
    public PermissionLevel requiredLevel(String toolName) {
        for (ToolPermission rule : rules) {
            if (rule.matches(toolName)) {
                return rule.required();
            }
        }
        return PermissionLevel.WORKSPACE_WRITE;
    }

    /**
     * Default policy with sensible tool mappings:
     * - file_read, grep, glob → READ_ONLY
     * - file_write, file_edit → WORKSPACE_WRITE
     * - bash → DANGER_FULL_ACCESS
     */
    public static PermissionPolicy defaultPolicy(PermissionLevel currentLevel) {
        List<ToolPermission> rules = List.of(
            new ToolPermission("file_read", PermissionLevel.READ_ONLY),
            new ToolPermission("grep", PermissionLevel.READ_ONLY),
            new ToolPermission("glob", PermissionLevel.READ_ONLY),
            new ToolPermission("file_write", PermissionLevel.WORKSPACE_WRITE),
            new ToolPermission("file_edit", PermissionLevel.WORKSPACE_WRITE),
            new ToolPermission("bash", PermissionLevel.DANGER_FULL_ACCESS)
        );
        return new PermissionPolicy(currentLevel, rules);
    }
}
