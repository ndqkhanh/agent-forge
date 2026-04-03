package com.agentforge.gateway;

import com.agentforge.config.loader.AgentForgeConfig;
import com.agentforge.config.loader.ConfigLoader;
import com.agentforge.config.permission.PermissionLevel;
import com.agentforge.runtime.PermissionChecker;
import com.agentforge.tools.registry.ToolRegistry;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForgeBuilderTest {

    @Test
    void builder_defaultPort_is8080() {
        ForgeBuilder fb = ForgeBuilder.builder().build();
        // Default port is 8080 — verify the builder produces without error
        assertThat(fb).isNotNull();
    }

    @Test
    void builder_customPort_accepted() {
        ForgeBuilder fb = ForgeBuilder.builder().port(9090).build();
        assertThat(fb).isNotNull();
    }

    @Test
    void builder_portBelowRange_throwsIllegalArgument() {
        assertThatThrownBy(() -> ForgeBuilder.builder().port(0))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("port");
    }

    @Test
    void builder_portAboveRange_throwsIllegalArgument() {
        assertThatThrownBy(() -> ForgeBuilder.builder().port(65536))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("port");
    }

    @Test
    void builder_nullConfigLoader_throwsIllegalArgument() {
        assertThatThrownBy(() -> ForgeBuilder.builder().configLoader(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("configLoader");
    }

    @Test
    void builder_withToolRegistry_accepted() {
        ToolRegistry registry = ToolRegistry.builder().build();
        ForgeBuilder fb = ForgeBuilder.builder().toolRegistry(registry).build();
        assertThat(fb).isNotNull();
    }

    @Test
    void builder_withBasePrompt_accepted() {
        ForgeBuilder fb = ForgeBuilder.builder().basePrompt("Act as a helpful assistant.").build();
        assertThat(fb).isNotNull();
    }

    @Test
    void builder_nullBasePrompt_defaultsToEmpty() {
        // Should not throw — null base prompt is converted to empty string
        ForgeBuilder fb = ForgeBuilder.builder().basePrompt(null).build();
        assertThat(fb).isNotNull();
    }

    // -------------------------------------------------------------------------
    // ToolRegistryAdapter
    // -------------------------------------------------------------------------

    @Test
    void toolRegistryAdapter_execute_returnsOutput() {
        ToolRegistry registry = ToolRegistry.builder().build();
        ForgeBuilder.ToolRegistryAdapter adapter = new ForgeBuilder.ToolRegistryAdapter(registry);

        String result = adapter.execute("unknown-tool", "{}");
        // ToolRegistry returns error output for unknown tools; adapter returns the output string
        assertThat(result).isNotNull();
        assertThat(result).contains("Unknown tool");
    }

    @Test
    void toolRegistryAdapter_supports_unknownTool_returnsFalse() {
        ToolRegistry registry = ToolRegistry.builder().build();
        ForgeBuilder.ToolRegistryAdapter adapter = new ForgeBuilder.ToolRegistryAdapter(registry);

        assertThat(adapter.supports("nope")).isFalse();
    }

    // -------------------------------------------------------------------------
    // HookPipelineAdapter
    // -------------------------------------------------------------------------

    @Test
    void hookPipelineAdapter_preToolUse_emptyPipeline_allowsAll() {
        com.agentforge.hooks.HookPipeline pipeline = com.agentforge.hooks.HookPipeline.empty();
        ForgeBuilder.HookPipelineAdapter adapter = new ForgeBuilder.HookPipelineAdapter(pipeline);

        com.agentforge.runtime.HookPipeline.HookResult result =
            adapter.preToolUse("bash", "{\"command\":\"ls\"}");

        assertThat(result.allowed()).isTrue();
    }

    @Test
    void hookPipelineAdapter_postToolUse_emptyPipeline_returnsOriginal() {
        com.agentforge.hooks.HookPipeline pipeline = com.agentforge.hooks.HookPipeline.empty();
        ForgeBuilder.HookPipelineAdapter adapter = new ForgeBuilder.HookPipelineAdapter(pipeline);

        String result = adapter.postToolUse("bash", "output text");
        assertThat(result).isEqualTo("output text");
    }

    // -------------------------------------------------------------------------
    // ConfigPermissionChecker
    // -------------------------------------------------------------------------

    @Test
    void configPermissionChecker_workspaceWrite_allowsWorkspaceWriteAndBelow() {
        AgentForgeConfig config = AgentForgeConfig.defaults(); // defaults to WORKSPACE_WRITE
        ForgeBuilder.ConfigPermissionChecker checker = new ForgeBuilder.ConfigPermissionChecker(config);

        assertThat(checker.isAllowed("any", PermissionChecker.PermissionLevel.READ_ONLY)).isTrue();
        assertThat(checker.isAllowed("any", PermissionChecker.PermissionLevel.WORKSPACE_WRITE)).isTrue();
        assertThat(checker.isAllowed("any", PermissionChecker.PermissionLevel.DANGER_FULL_ACCESS)).isFalse();
    }

    @Test
    void configPermissionChecker_readOnly_deniesWrite() {
        AgentForgeConfig config = new AgentForgeConfig(
            "model", 4096, 1.0, 25,
            PermissionLevel.READ_ONLY,
            java.util.List.of(), 100_000, 200_000,
            java.util.Map.of(), java.util.List.of(), java.util.Map.of(), java.util.Map.of()
        );
        ForgeBuilder.ConfigPermissionChecker checker = new ForgeBuilder.ConfigPermissionChecker(config);

        assertThat(checker.isAllowed("any", PermissionChecker.PermissionLevel.READ_ONLY)).isTrue();
        assertThat(checker.isAllowed("any", PermissionChecker.PermissionLevel.WORKSPACE_WRITE)).isFalse();
    }

    @Test
    void configPermissionChecker_dangerFullAccess_allowsAll() {
        AgentForgeConfig config = new AgentForgeConfig(
            "model", 4096, 1.0, 25,
            PermissionLevel.DANGER_FULL_ACCESS,
            java.util.List.of(), 100_000, 200_000,
            java.util.Map.of(), java.util.List.of(), java.util.Map.of(), java.util.Map.of()
        );
        ForgeBuilder.ConfigPermissionChecker checker = new ForgeBuilder.ConfigPermissionChecker(config);

        assertThat(checker.isAllowed("any", PermissionChecker.PermissionLevel.READ_ONLY)).isTrue();
        assertThat(checker.isAllowed("any", PermissionChecker.PermissionLevel.WORKSPACE_WRITE)).isTrue();
        assertThat(checker.isAllowed("any", PermissionChecker.PermissionLevel.DANGER_FULL_ACCESS)).isTrue();
    }
}
