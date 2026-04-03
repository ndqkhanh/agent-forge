package com.agentforge.config.loader;

import com.agentforge.common.error.ConfigException;
import com.agentforge.config.permission.PermissionLevel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("ConfigLoader")
class ConfigLoaderTest {

    @TempDir
    Path tempDir;

    private Path writeJson(String filename, String content) throws IOException {
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    @Test
    @DisplayName("load with no config files returns defaults")
    void load_noConfigFiles_returnsDefaults() {
        var loader = ConfigLoader.builder().build();
        var config = loader.load();
        assertThat(config).isEqualTo(AgentForgeConfig.defaults());
    }

    @Test
    @DisplayName("load with null paths returns defaults")
    void load_nullPaths_returnsDefaults() {
        var loader = ConfigLoader.builder()
            .userConfig(null)
            .projectConfig(null)
            .localConfig(null)
            .build();
        var config = loader.load();
        assertThat(config).isEqualTo(AgentForgeConfig.defaults());
    }

    @Test
    @DisplayName("load overrides model from user config")
    void load_userConfig_overridesModel() throws IOException {
        var userConfig = writeJson("user.json", """
            {"model": "claude-opus-4"}
            """);
        var loader = ConfigLoader.builder().userConfig(userConfig).build();
        var config = loader.load();
        assertThat(config.model()).isEqualTo("claude-opus-4");
    }

    @Test
    @DisplayName("load overrides maxTokens from project config")
    void load_projectConfig_overridesMaxTokens() throws IOException {
        var projectConfig = writeJson("project.json", """
            {"maxTokens": 8192}
            """);
        var loader = ConfigLoader.builder().projectConfig(projectConfig).build();
        var config = loader.load();
        assertThat(config.maxTokens()).isEqualTo(8192);
    }

    @Test
    @DisplayName("load overrides temperature from local config")
    void load_localConfig_overridesTemperature() throws IOException {
        var localConfig = writeJson("local.json", """
            {"temperature": 0.7}
            """);
        var loader = ConfigLoader.builder().localConfig(localConfig).build();
        var config = loader.load();
        assertThat(config.temperature()).isEqualTo(0.7);
    }

    @Test
    @DisplayName("local config overrides project config (priority)")
    void load_localOverridesProject() throws IOException {
        var projectConfig = writeJson("project.json", """
            {"model": "claude-haiku-4"}
            """);
        var localConfig = writeJson("local.json", """
            {"model": "claude-opus-4"}
            """);
        var loader = ConfigLoader.builder()
            .projectConfig(projectConfig)
            .localConfig(localConfig)
            .build();
        var config = loader.load();
        assertThat(config.model()).isEqualTo("claude-opus-4");
    }

    @Test
    @DisplayName("project config overrides user config (priority)")
    void load_projectOverridesUser() throws IOException {
        var userConfig = writeJson("user.json", """
            {"model": "claude-haiku-4"}
            """);
        var projectConfig = writeJson("project.json", """
            {"model": "claude-sonnet-4"}
            """);
        var loader = ConfigLoader.builder()
            .userConfig(userConfig)
            .projectConfig(projectConfig)
            .build();
        var config = loader.load();
        assertThat(config.model()).isEqualTo("claude-sonnet-4");
    }

    @Test
    @DisplayName("load overrides permissionLevel from config")
    void load_overridesPermissionLevel() throws IOException {
        var projectConfig = writeJson("project.json", """
            {"permissionLevel": "READ_ONLY"}
            """);
        var loader = ConfigLoader.builder().projectConfig(projectConfig).build();
        var config = loader.load();
        assertThat(config.permissionLevel()).isEqualTo(PermissionLevel.READ_ONLY);
    }

    @Test
    @DisplayName("unrecognized permissionLevel value keeps existing level")
    void load_unrecognizedPermissionLevel_keepsExisting() throws IOException {
        var projectConfig = writeJson("project.json", """
            {"permissionLevel": "INVALID_LEVEL"}
            """);
        var loader = ConfigLoader.builder().projectConfig(projectConfig).build();
        var config = loader.load();
        assertThat(config.permissionLevel()).isEqualTo(PermissionLevel.WORKSPACE_WRITE);
    }

    @Test
    @DisplayName("load overrides compactionThreshold")
    void load_overridesCompactionThreshold() throws IOException {
        var userConfig = writeJson("user.json", """
            {"compactionThreshold": 50000}
            """);
        var loader = ConfigLoader.builder().userConfig(userConfig).build();
        var config = loader.load();
        assertThat(config.compactionThreshold()).isEqualTo(50000);
    }

    @Test
    @DisplayName("load overrides contextWindow")
    void load_overridesContextWindow() throws IOException {
        var userConfig = writeJson("user.json", """
            {"contextWindow": 128000}
            """);
        var loader = ConfigLoader.builder().userConfig(userConfig).build();
        var config = loader.load();
        assertThat(config.contextWindow()).isEqualTo(128000);
    }

    @Test
    @DisplayName("load merges providerApiKeys from config")
    void load_mergesProviderApiKeys() throws IOException {
        var projectConfig = writeJson("project.json", """
            {"providerApiKeys": {"anthropic": "sk-test-123", "openai": "sk-openai-456"}}
            """);
        var loader = ConfigLoader.builder().projectConfig(projectConfig).build();
        var config = loader.load();
        assertThat(config.providerApiKeys())
            .containsEntry("anthropic", "sk-test-123")
            .containsEntry("openai", "sk-openai-456");
    }

    @Test
    @DisplayName("load accumulates providerApiKeys across layers")
    void load_accumulatesProviderApiKeys_acrossLayers() throws IOException {
        var userConfig = writeJson("user.json", """
            {"providerApiKeys": {"anthropic": "user-key"}}
            """);
        var projectConfig = writeJson("project.json", """
            {"providerApiKeys": {"openai": "project-key"}}
            """);
        var loader = ConfigLoader.builder()
            .userConfig(userConfig)
            .projectConfig(projectConfig)
            .build();
        var config = loader.load();
        assertThat(config.providerApiKeys())
            .containsEntry("anthropic", "user-key")
            .containsEntry("openai", "project-key");
    }

    @Test
    @DisplayName("higher priority layer overwrites same providerApiKey")
    void load_higherLayerOverwritesApiKey() throws IOException {
        var userConfig = writeJson("user.json", """
            {"providerApiKeys": {"anthropic": "user-key"}}
            """);
        var localConfig = writeJson("local.json", """
            {"providerApiKeys": {"anthropic": "local-key"}}
            """);
        var loader = ConfigLoader.builder()
            .userConfig(userConfig)
            .localConfig(localConfig)
            .build();
        var config = loader.load();
        assertThat(config.providerApiKeys()).containsEntry("anthropic", "local-key");
    }

    @Test
    @DisplayName("load overrides instructionFiles from config")
    void load_overridesInstructionFiles() throws IOException {
        var projectConfig = writeJson("project.json", """
            {"instructionFiles": ["/path/to/rules.md", "/path/to/more.md"]}
            """);
        var loader = ConfigLoader.builder().projectConfig(projectConfig).build();
        var config = loader.load();
        assertThat(config.instructionFiles())
            .containsExactly("/path/to/rules.md", "/path/to/more.md");
    }

    @Test
    @DisplayName("load overrides mcpServers from config")
    void load_overridesMcpServers() throws IOException {
        var projectConfig = writeJson("project.json", """
            {"mcpServers": {"myServer": {"url": "http://localhost:8080"}}}
            """);
        var loader = ConfigLoader.builder().projectConfig(projectConfig).build();
        var config = loader.load();
        assertThat(config.mcpServers()).containsKey("myServer");
    }

    @Test
    @DisplayName("unknown keys are stored in extra as strings")
    void load_unknownStringKeys_storedInExtra() throws IOException {
        var projectConfig = writeJson("project.json", """
            {"customKey": "customValue"}
            """);
        var loader = ConfigLoader.builder().projectConfig(projectConfig).build();
        var config = loader.load();
        assertThat(config.extra()).containsEntry("customKey", "customValue");
    }

    @Test
    @DisplayName("non-string unknown key values are not stored in extra")
    void load_unknownNonStringKeys_notStoredInExtra() throws IOException {
        var projectConfig = writeJson("project.json", """
            {"numericExtra": 42}
            """);
        var loader = ConfigLoader.builder().projectConfig(projectConfig).build();
        var config = loader.load();
        assertThat(config.extra()).doesNotContainKey("numericExtra");
    }

    @Test
    @DisplayName("throws ConfigException for invalid JSON")
    void load_invalidJson_throwsConfigException() throws IOException {
        var badConfig = writeJson("bad.json", "{ not valid json");
        var loader = ConfigLoader.builder().userConfig(badConfig).build();
        assertThatThrownBy(loader::load)
            .isInstanceOf(ConfigException.class);
    }

    @Test
    @DisplayName("throws ConfigException for JSON that is not an object")
    void load_jsonNotObject_throwsConfigException() throws IOException {
        var badConfig = writeJson("array.json", "[1, 2, 3]");
        var loader = ConfigLoader.builder().userConfig(badConfig).build();
        assertThatThrownBy(loader::load)
            .isInstanceOf(ConfigException.class)
            .hasMessageContaining("JSON object");
    }

    @Test
    @DisplayName("non-existent path is silently skipped")
    void load_nonExistentPath_skipped() {
        var loader = ConfigLoader.builder()
            .userConfig(tempDir.resolve("does-not-exist.json"))
            .build();
        var config = loader.load();
        assertThat(config).isEqualTo(AgentForgeConfig.defaults());
    }

    @Test
    @DisplayName("empty JSON object leaves defaults unchanged")
    void load_emptyJsonObject_keepsDefaults() throws IOException {
        var projectConfig = writeJson("project.json", "{}");
        var loader = ConfigLoader.builder().projectConfig(projectConfig).build();
        var config = loader.load();
        assertThat(config).isEqualTo(AgentForgeConfig.defaults());
    }

    @Test
    @DisplayName("builder builds a loader with all three paths")
    void builder_allThreePaths() throws IOException {
        var userConfig = writeJson("user.json", "{\"model\": \"model-user\"}");
        var projectConfig = writeJson("project.json", "{\"maxTokens\": 1000}");
        var localConfig = writeJson("local.json", "{\"temperature\": 0.5}");

        var loader = ConfigLoader.builder()
            .userConfig(userConfig)
            .projectConfig(projectConfig)
            .localConfig(localConfig)
            .build();

        var config = loader.load();
        assertThat(config.model()).isEqualTo("model-user");
        assertThat(config.maxTokens()).isEqualTo(1000);
        assertThat(config.temperature()).isEqualTo(0.5);
    }

    @Test
    @DisplayName("non-matching type for maxTokens field is ignored")
    void load_wrongTypeForMaxTokens_ignored() throws IOException {
        var projectConfig = writeJson("project.json", """
            {"maxTokens": "not-a-number"}
            """);
        var loader = ConfigLoader.builder().projectConfig(projectConfig).build();
        var config = loader.load();
        // should keep default since string doesn't match JsonNumber
        assertThat(config.maxTokens()).isEqualTo(AgentForgeConfig.defaults().maxTokens());
    }
}
