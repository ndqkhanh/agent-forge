package com.agentforge.config.loader;

import com.agentforge.config.permission.PermissionLevel;
import com.agentforge.config.permission.ToolPermission;

import java.util.List;
import java.util.Map;

public record AgentForgeConfig(
    String model,
    int maxTokens,
    double temperature,
    int maxIterations,
    PermissionLevel permissionLevel,
    List<ToolPermission> toolPermissions,
    int compactionThreshold,
    int contextWindow,
    Map<String, String> providerApiKeys,
    List<String> instructionFiles,
    Map<String, Object> mcpServers,
    Map<String, String> extra
) {
    public AgentForgeConfig {
        toolPermissions = List.copyOf(toolPermissions);
        providerApiKeys = Map.copyOf(providerApiKeys);
        instructionFiles = List.copyOf(instructionFiles);
        mcpServers = Map.copyOf(mcpServers);
        extra = Map.copyOf(extra);
    }

    public static AgentForgeConfig defaults() {
        return new AgentForgeConfig(
            "claude-sonnet-4-6",
            4096,
            1.0,
            25,
            PermissionLevel.WORKSPACE_WRITE,
            List.of(),
            100_000,
            200_000,
            Map.of(),
            List.of(),
            Map.of(),
            Map.of()
        );
    }
}
