package com.agentforge.config.permission;

public record ToolPermission(String toolPattern, PermissionLevel required) {

    /**
     * Check if the given tool name matches this permission's pattern.
     * Supports exact match and wildcard patterns (e.g. "file_*", "*").
     */
    public boolean matches(String toolName) {
        if (toolPattern == null || toolName == null) {
            return false;
        }
        if (toolPattern.equals("*")) {
            return true;
        }
        if (toolPattern.endsWith("*")) {
            String prefix = toolPattern.substring(0, toolPattern.length() - 1);
            return toolName.startsWith(prefix);
        }
        if (toolPattern.startsWith("*")) {
            String suffix = toolPattern.substring(1);
            return toolName.endsWith(suffix);
        }
        return toolPattern.equals(toolName);
    }
}
