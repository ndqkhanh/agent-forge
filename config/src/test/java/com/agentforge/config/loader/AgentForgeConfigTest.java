package com.agentforge.config.loader;

import com.agentforge.config.permission.PermissionLevel;
import com.agentforge.config.permission.ToolPermission;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("AgentForgeConfig")
class AgentForgeConfigTest {

    @Test
    @DisplayName("defaults() returns expected default model")
    void defaults_model() {
        var config = AgentForgeConfig.defaults();
        assertThat(config.model()).isEqualTo("claude-sonnet-4-6");
    }

    @Test
    @DisplayName("defaults() returns expected maxTokens")
    void defaults_maxTokens() {
        var config = AgentForgeConfig.defaults();
        assertThat(config.maxTokens()).isEqualTo(4096);
    }

    @Test
    @DisplayName("defaults() returns expected temperature")
    void defaults_temperature() {
        var config = AgentForgeConfig.defaults();
        assertThat(config.temperature()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("defaults() returns expected maxIterations")
    void defaults_maxIterations() {
        var config = AgentForgeConfig.defaults();
        assertThat(config.maxIterations()).isEqualTo(25);
    }

    @Test
    @DisplayName("defaults() returns WORKSPACE_WRITE permission level")
    void defaults_permissionLevel() {
        var config = AgentForgeConfig.defaults();
        assertThat(config.permissionLevel()).isEqualTo(PermissionLevel.WORKSPACE_WRITE);
    }

    @Test
    @DisplayName("defaults() returns empty toolPermissions")
    void defaults_emptyToolPermissions() {
        var config = AgentForgeConfig.defaults();
        assertThat(config.toolPermissions()).isEmpty();
    }

    @Test
    @DisplayName("defaults() returns expected compactionThreshold")
    void defaults_compactionThreshold() {
        var config = AgentForgeConfig.defaults();
        assertThat(config.compactionThreshold()).isEqualTo(100_000);
    }

    @Test
    @DisplayName("defaults() returns expected contextWindow")
    void defaults_contextWindow() {
        var config = AgentForgeConfig.defaults();
        assertThat(config.contextWindow()).isEqualTo(200_000);
    }

    @Test
    @DisplayName("defaults() returns empty maps and lists")
    void defaults_emptyCollections() {
        var config = AgentForgeConfig.defaults();
        assertThat(config.providerApiKeys()).isEmpty();
        assertThat(config.instructionFiles()).isEmpty();
        assertThat(config.mcpServers()).isEmpty();
        assertThat(config.extra()).isEmpty();
    }

    @Test
    @DisplayName("toolPermissions list is immutable — mutation throws")
    void toolPermissions_isImmutable() {
        var mutableList = new ArrayList<ToolPermission>();
        mutableList.add(new ToolPermission("file_read", PermissionLevel.READ_ONLY));
        var config = new AgentForgeConfig(
            "model", 1000, 0.5, 10,
            PermissionLevel.READ_ONLY,
            mutableList,
            50_000, 100_000,
            Map.of(), List.of(), Map.of(), Map.of()
        );

        assertThatThrownBy(() -> config.toolPermissions().add(new ToolPermission("bash", PermissionLevel.DANGER_FULL_ACCESS)))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("providerApiKeys map is immutable — mutation throws")
    void providerApiKeys_isImmutable() {
        var mutableMap = new HashMap<String, String>();
        mutableMap.put("anthropic", "key-123");
        var config = new AgentForgeConfig(
            "model", 1000, 0.5, 10,
            PermissionLevel.READ_ONLY,
            List.of(),
            50_000, 100_000,
            mutableMap, List.of(), Map.of(), Map.of()
        );

        assertThatThrownBy(() -> config.providerApiKeys().put("openai", "other-key"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("instructionFiles list is immutable — mutation throws")
    void instructionFiles_isImmutable() {
        var mutableList = new ArrayList<String>();
        mutableList.add("/path/to/instructions.md");
        var config = new AgentForgeConfig(
            "model", 1000, 0.5, 10,
            PermissionLevel.READ_ONLY,
            List.of(),
            50_000, 100_000,
            Map.of(), mutableList, Map.of(), Map.of()
        );

        assertThatThrownBy(() -> config.instructionFiles().add("new-file.md"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("mcpServers map is immutable — mutation throws")
    void mcpServers_isImmutable() {
        var mutableMap = new HashMap<String, Object>();
        mutableMap.put("server1", Map.of("url", "http://localhost"));
        var config = new AgentForgeConfig(
            "model", 1000, 0.5, 10,
            PermissionLevel.READ_ONLY,
            List.of(),
            50_000, 100_000,
            Map.of(), List.of(), mutableMap, Map.of()
        );

        assertThatThrownBy(() -> config.mcpServers().put("server2", "value"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("extra map is immutable — mutation throws")
    void extra_isImmutable() {
        var mutableMap = new HashMap<String, String>();
        mutableMap.put("custom_key", "value");
        var config = new AgentForgeConfig(
            "model", 1000, 0.5, 10,
            PermissionLevel.READ_ONLY,
            List.of(),
            50_000, 100_000,
            Map.of(), List.of(), Map.of(), mutableMap
        );

        assertThatThrownBy(() -> config.extra().put("new_key", "new_value"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("external mutation of source list does not affect config toolPermissions")
    void toolPermissions_defensiveCopy_externalMutationIgnored() {
        var mutableList = new ArrayList<ToolPermission>();
        mutableList.add(new ToolPermission("file_read", PermissionLevel.READ_ONLY));
        var config = new AgentForgeConfig(
            "model", 1000, 0.5, 10,
            PermissionLevel.READ_ONLY,
            mutableList,
            50_000, 100_000,
            Map.of(), List.of(), Map.of(), Map.of()
        );

        mutableList.add(new ToolPermission("bash", PermissionLevel.DANGER_FULL_ACCESS));

        assertThat(config.toolPermissions()).hasSize(1);
    }

    @Test
    @DisplayName("two configs with same values are equal (record equality)")
    void equals_sameValues() {
        var c1 = AgentForgeConfig.defaults();
        var c2 = AgentForgeConfig.defaults();
        assertThat(c1).isEqualTo(c2);
        assertThat(c1.hashCode()).isEqualTo(c2.hashCode());
    }

    @Test
    @DisplayName("toString contains model name")
    void toString_containsModel() {
        var config = AgentForgeConfig.defaults();
        assertThat(config.toString()).contains("claude-sonnet-4-6");
    }
}
