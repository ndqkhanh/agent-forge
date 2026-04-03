package com.agentforge.tools.builtin;

import com.agentforge.tools.ToolExecutor.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("FileEditTool")
class FileEditToolTest {

    @TempDir
    Path tempDir;

    FileEditTool tool;

    @BeforeEach
    void setUp() {
        tool = new FileEditTool(tempDir);
    }

    // --- Successful replacement ---

    @Test
    @DisplayName("replaces unique occurrence of old_string with new_string")
    void execute_uniqueOccurrence_replaces() throws IOException {
        Path file = tempDir.resolve("src.txt");
        Files.writeString(file, "Hello foo World");

        ToolResult result = tool.execute(
                "{\"path\": \"src.txt\", \"old_string\": \"foo\", \"new_string\": \"bar\"}");

        assertThat(result.isError()).isFalse();
        assertThat(Files.readString(file)).isEqualTo("Hello bar World");
    }

    @Test
    @DisplayName("replaces multi-line old_string")
    void execute_multiLineOldString_replaces() throws IOException {
        Path file = tempDir.resolve("multi.txt");
        Files.writeString(file, "line1\nline2\nline3\n");

        ToolResult result = tool.execute(
                "{\"path\": \"multi.txt\", \"old_string\": \"line1\\nline2\", \"new_string\": \"replaced\"}");

        assertThat(result.isError()).isFalse();
        assertThat(Files.readString(file)).isEqualTo("replaced\nline3\n");
    }

    // --- Error cases ---

    @Test
    @DisplayName("old_string not found returns error")
    void execute_oldStringNotFound_returnsError() throws IOException {
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "content here");

        ToolResult result = tool.execute(
                "{\"path\": \"file.txt\", \"old_string\": \"missing\", \"new_string\": \"x\"}");

        assertThat(result.isError()).isTrue();
        assertThat(result.output()).containsIgnoringCase("not found");
    }

    @Test
    @DisplayName("multiple occurrences of old_string returns error")
    void execute_multipleOccurrences_returnsError() throws IOException {
        Path file = tempDir.resolve("dupe.txt");
        Files.writeString(file, "foo bar foo");

        ToolResult result = tool.execute(
                "{\"path\": \"dupe.txt\", \"old_string\": \"foo\", \"new_string\": \"baz\"}");

        assertThat(result.isError()).isTrue();
        assertThat(result.output().toLowerCase()).satisfiesAnyOf(
                s -> assertThat(s).contains("multiple"),
                s -> assertThat(s).contains("unique"));
    }

    @Test
    @DisplayName("file not found returns error")
    void execute_fileNotFound_returnsError() {
        ToolResult result = tool.execute(
                "{\"path\": \"ghost.txt\", \"old_string\": \"x\", \"new_string\": \"y\"}");

        assertThat(result.isError()).isTrue();
        assertThat(result.output()).containsIgnoringCase("not found");
    }

    @Test
    @DisplayName("path traversal is blocked")
    void execute_pathTraversal_blocked() {
        ToolResult result = tool.execute(
                "{\"path\": \"../../etc/passwd\", \"old_string\": \"root\", \"new_string\": \"evil\"}");

        assertThat(result.isError()).isTrue();
        assertThat(result.output().toLowerCase()).satisfiesAnyOf(
                s -> assertThat(s).contains("traversal"),
                s -> assertThat(s).contains("denied"));
    }

    @Test
    @DisplayName("empty old_string returns error")
    void execute_emptyOldString_returnsError() throws IOException {
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "content");

        ToolResult result = tool.execute(
                "{\"path\": \"file.txt\", \"old_string\": \"\", \"new_string\": \"x\"}");

        assertThat(result.isError()).isTrue();
        assertThat(result.output().toLowerCase()).satisfiesAnyOf(
                s -> assertThat(s).contains("empty"),
                s -> assertThat(s).contains("old_string"));
    }

    @Test
    @DisplayName("missing path field returns error")
    void execute_missingPath_returnsError() {
        ToolResult result = tool.execute("{\"old_string\": \"x\", \"new_string\": \"y\"}");

        assertThat(result.isError()).isTrue();
        assertThat(result.output()).contains("path");
    }

    @Test
    @DisplayName("missing old_string field returns error")
    void execute_missingOldString_returnsError() {
        ToolResult result = tool.execute("{\"path\": \"file.txt\", \"new_string\": \"y\"}");

        assertThat(result.isError()).isTrue();
    }

    // --- Schema ---

    @Test
    @DisplayName("inputSchema() is valid JSON containing required fields")
    void inputSchema_isValidJson() {
        String schema = tool.inputSchema();
        assertThat(schema).isNotBlank();
        assertThat(schema).contains("path");
        assertThat(schema).contains("old_string");
        assertThat(schema).contains("new_string");
    }
}
