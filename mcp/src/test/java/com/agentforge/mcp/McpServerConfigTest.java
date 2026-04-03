package com.agentforge.mcp;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("McpServerConfig")
class McpServerConfigTest {

    @Test
    @DisplayName("constructor stores all fields")
    void constructor_storesAllFields() {
        McpServerConfig config = new McpServerConfig(
                "my-server", "stdio", "python", List.of("-m", "mcp"),
                null, Map.of("ENV_VAR", "value"));

        assertThat(config.name()).isEqualTo("my-server");
        assertThat(config.transportType()).isEqualTo("stdio");
        assertThat(config.command()).isEqualTo("python");
        assertThat(config.args()).containsExactly("-m", "mcp");
        assertThat(config.env()).containsEntry("ENV_VAR", "value");
    }

    @Test
    @DisplayName("null args defaults to empty list")
    void constructor_nullArgs_defaultsToEmptyList() {
        McpServerConfig config = new McpServerConfig(
                "srv", "http", null, null, "http://localhost:8080", null);

        assertThat(config.args()).isEmpty();
    }

    @Test
    @DisplayName("null env defaults to empty map")
    void constructor_nullEnv_defaultsToEmptyMap() {
        McpServerConfig config = new McpServerConfig(
                "srv", "http", null, null, "http://localhost:8080", null);

        assertThat(config.env()).isEmpty();
    }

    @Test
    @DisplayName("args list is immutable defensive copy")
    void constructor_argsList_isImmutable() {
        List<String> mutableArgs = new java.util.ArrayList<>(List.of("--flag"));
        McpServerConfig config = new McpServerConfig(
                "srv", "stdio", "node", mutableArgs, null, null);
        mutableArgs.add("--extra");

        assertThat(config.args()).containsExactly("--flag");
    }

    @Test
    @DisplayName("env map is immutable defensive copy")
    void constructor_envMap_isImmutable() {
        Map<String, String> mutableEnv = new java.util.HashMap<>(Map.of("K", "V"));
        McpServerConfig config = new McpServerConfig(
                "srv", "stdio", "node", null, null, mutableEnv);
        mutableEnv.put("OTHER", "extra");

        assertThat(config.env()).containsOnlyKeys("K");
    }

    @Test
    @DisplayName("blank name throws IllegalArgumentException")
    void constructor_blankName_throws() {
        assertThatThrownBy(() -> new McpServerConfig(
                "  ", "stdio", "cmd", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("null name throws IllegalArgumentException")
    void constructor_nullName_throws() {
        assertThatThrownBy(() -> new McpServerConfig(
                null, "stdio", "cmd", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("blank transportType throws IllegalArgumentException")
    void constructor_blankTransportType_throws() {
        assertThatThrownBy(() -> new McpServerConfig(
                "srv", "   ", "cmd", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transportType");
    }

    @Test
    @DisplayName("null transportType throws IllegalArgumentException")
    void constructor_nullTransportType_throws() {
        assertThatThrownBy(() -> new McpServerConfig(
                "srv", null, "cmd", null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transportType");
    }

    @Test
    @DisplayName("http config with url is valid")
    void constructor_httpConfig_isValid() {
        McpServerConfig config = new McpServerConfig(
                "http-server", "http", null, null,
                "http://example.com/mcp", Map.of("Authorization", "Bearer token"));

        assertThat(config.url()).isEqualTo("http://example.com/mcp");
        assertThat(config.transportType()).isEqualTo("http");
    }

    @Test
    @DisplayName("record equality holds for identical values")
    void recordEquality_identicalValues_areEqual() {
        McpServerConfig a = new McpServerConfig("s", "stdio", "cmd", List.of(), null, Map.of());
        McpServerConfig b = new McpServerConfig("s", "stdio", "cmd", List.of(), null, Map.of());

        assertThat(a).isEqualTo(b);
        assertThat(a.hashCode()).isEqualTo(b.hashCode());
    }
}
