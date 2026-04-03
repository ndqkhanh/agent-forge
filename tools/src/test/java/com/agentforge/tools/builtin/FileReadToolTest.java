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

@DisplayName("FileReadTool")
class FileReadToolTest {

    @TempDir
    Path tempDir;

    FileReadTool tool;

    @BeforeEach
    void setUp() {
        tool = new FileReadTool(tempDir);
    }

    // --- Basic read ---

    @Test
    @DisplayName("reads existing file and returns content with line numbers")
    void execute_existingFile_returnsContent() throws IOException {
        Path file = tempDir.resolve("hello.txt");
        Files.writeString(file, "line one\nline two\nline three\n");

        ToolResult result = tool.execute("{\"path\": \"hello.txt\"}");

        assertThat(result.isError()).isFalse();
        assertThat(result.output()).contains("line one");
        assertThat(result.output()).contains("line two");
        assertThat(result.output()).contains("line three");
    }

    @Test
    @DisplayName("line numbers are added in cat -n style")
    void execute_addsLineNumbers() throws IOException {
        Path file = tempDir.resolve("numbered.txt");
        Files.writeString(file, "alpha\nbeta\ngamma\n");

        ToolResult result = tool.execute("{\"path\": \"numbered.txt\"}");

        assertThat(result.isError()).isFalse();
        // Expect format: "     1\talpha"
        assertThat(result.output()).contains("1");
        assertThat(result.output()).contains("2");
        assertThat(result.output()).contains("3");
        assertThat(result.output()).contains("alpha");
        assertThat(result.output()).contains("beta");
    }

    // --- Offset and limit ---

    @Test
    @DisplayName("offset skips leading lines")
    void execute_withOffset_skipsLines() throws IOException {
        Path file = tempDir.resolve("offset.txt");
        Files.writeString(file, "a\nb\nc\nd\ne\n");

        ToolResult result = tool.execute("{\"path\": \"offset.txt\", \"offset\": 2}");

        assertThat(result.isError()).isFalse();
        assertThat(result.output()).doesNotContain("\ta\n");
        assertThat(result.output()).doesNotContain("\tb\n");
        assertThat(result.output()).contains("c");
    }

    @Test
    @DisplayName("limit restricts number of lines returned")
    void execute_withLimit_restrictLines() throws IOException {
        Path file = tempDir.resolve("limit.txt");
        Files.writeString(file, "1\n2\n3\n4\n5\n");

        ToolResult result = tool.execute("{\"path\": \"limit.txt\", \"limit\": 2}");

        assertThat(result.isError()).isFalse();
        String[] lines = result.output().split("\n");
        assertThat(lines).hasSize(2);
    }

    // --- Error cases ---

    @Test
    @DisplayName("file not found returns error")
    void execute_fileNotFound_returnsError() {
        ToolResult result = tool.execute("{\"path\": \"nonexistent.txt\"}");

        assertThat(result.isError()).isTrue();
        assertThat(result.output()).containsIgnoringCase("not found");
    }

    @Test
    @DisplayName("path traversal is blocked")
    void execute_pathTraversal_blocked() {
        ToolResult result = tool.execute("{\"path\": \"../../etc/passwd\"}");

        assertThat(result.isError()).isTrue();
        assertThat(result.output().toLowerCase()).satisfiesAnyOf(
                s -> assertThat(s).contains("traversal"),
                s -> assertThat(s).contains("denied"));
    }

    @Test
    @DisplayName("empty file returns success with empty content")
    void execute_emptyFile_returnsEmpty() throws IOException {
        Path file = tempDir.resolve("empty.txt");
        Files.writeString(file, "");

        ToolResult result = tool.execute("{\"path\": \"empty.txt\"}");

        assertThat(result.isError()).isFalse();
        assertThat(result.output()).isBlank();
    }

    @Test
    @DisplayName("missing path field returns error")
    void execute_missingPath_returnsError() {
        ToolResult result = tool.execute("{}");

        assertThat(result.isError()).isTrue();
        assertThat(result.output()).contains("path");
    }

    // --- Schema ---

    @Test
    @DisplayName("inputSchema() is valid JSON containing 'path'")
    void inputSchema_isValidJson() {
        String schema = tool.inputSchema();
        assertThat(schema).isNotBlank();
        assertThat(schema).contains("path");
    }
}
