package com.agentforge.hooks;

import com.agentforge.common.error.HookException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HookConfigLoaderTest {

    private static final String VALID_JSON = """
            [
              { "name": "security", "type": "pre_tool_use", "command": "check.sh", "tools": ["*"] },
              { "name": "format",   "type": "post_tool_use", "command": "fmt.sh",   "tools": ["file_write", "file_edit"] }
            ]
            """;

    @Test
    @DisplayName("parse valid JSON array produces correct definitions")
    void validJson_parsesCorrectly() {
        List<HookDefinition> defs = HookConfigLoader.load(VALID_JSON);
        assertThat(defs).hasSize(2);

        HookDefinition security = defs.get(0);
        assertThat(security.name()).isEqualTo("security");
        assertThat(security.type()).isEqualTo(HookType.PRE_TOOL_USE);
        assertThat(security.command()).isEqualTo("check.sh");
        assertThat(security.toolPatterns()).containsExactly("*");

        HookDefinition format = defs.get(1);
        assertThat(format.name()).isEqualTo("format");
        assertThat(format.type()).isEqualTo(HookType.POST_TOOL_USE);
        assertThat(format.toolPatterns()).containsExactly("file_write", "file_edit");
    }

    @Test
    @DisplayName("empty JSON array produces empty list")
    void emptyArray_producesEmptyList() {
        List<HookDefinition> defs = HookConfigLoader.load("[]");
        assertThat(defs).isEmpty();
    }

    @Test
    @DisplayName("missing required field 'name' throws HookException")
    void missingName_throwsHookException() {
        String json = """
                [{ "type": "pre_tool_use", "command": "x.sh", "tools": ["*"] }]
                """;
        assertThatThrownBy(() -> HookConfigLoader.load(json))
                .isInstanceOf(HookException.class)
                .hasMessageContaining("name");
    }

    @Test
    @DisplayName("missing required field 'command' throws HookException")
    void missingCommand_throwsHookException() {
        String json = """
                [{ "name": "x", "type": "pre_tool_use", "tools": ["*"] }]
                """;
        assertThatThrownBy(() -> HookConfigLoader.load(json))
                .isInstanceOf(HookException.class)
                .hasMessageContaining("command");
    }

    @Test
    @DisplayName("invalid type value throws HookException")
    void invalidType_throwsHookException() {
        String json = """
                [{ "name": "x", "type": "unknown_type", "command": "x.sh", "tools": ["*"] }]
                """;
        assertThatThrownBy(() -> HookConfigLoader.load(json))
                .isInstanceOf(HookException.class)
                .hasMessageContaining("invalid type");
    }

    @Test
    @DisplayName("invalid JSON string throws HookException")
    void invalidJson_throwsHookException() {
        assertThatThrownBy(() -> HookConfigLoader.load("not valid json {{{"))
                .isInstanceOf(HookException.class)
                .hasMessageContaining("Invalid hook config JSON");
    }

    @Test
    @DisplayName("top-level JSON object (not array) throws HookException")
    void topLevelObject_throwsHookException() {
        assertThatThrownBy(() -> HookConfigLoader.load("{\"name\":\"x\"}"))
                .isInstanceOf(HookException.class)
                .hasMessageContaining("JSON array");
    }

    @Test
    @DisplayName("null JSON string throws HookException")
    void nullJson_throwsHookException() {
        assertThatThrownBy(() -> HookConfigLoader.load(null))
                .isInstanceOf(HookException.class);
    }

    @Test
    @DisplayName("loadFromFile reads and parses config file")
    void loadFromFile_readsAndParsesFile(@TempDir Path tempDir) throws IOException {
        Path configFile = tempDir.resolve("hooks.json");
        Files.writeString(configFile, VALID_JSON, StandardCharsets.UTF_8);

        List<HookDefinition> defs = HookConfigLoader.loadFromFile(configFile);
        assertThat(defs).hasSize(2);
        assertThat(defs.get(0).name()).isEqualTo("security");
    }

    @Test
    @DisplayName("loadFromFile with missing file throws HookException")
    void loadFromFile_missingFile_throwsHookException(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("nonexistent.json");
        assertThatThrownBy(() -> HookConfigLoader.loadFromFile(missing))
                .isInstanceOf(HookException.class)
                .hasMessageContaining("Failed to read hook config file");
    }

    @Test
    @DisplayName("returned list is immutable")
    void returnedList_isImmutable() {
        List<HookDefinition> defs = HookConfigLoader.load(VALID_JSON);
        assertThatThrownBy(() -> defs.add(new HookDefinition("x", HookType.PRE_TOOL_USE, "x", List.of())))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
