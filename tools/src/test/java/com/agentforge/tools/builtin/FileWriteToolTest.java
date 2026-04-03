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

@DisplayName("FileWriteTool")
class FileWriteToolTest {

    @TempDir
    Path tempDir;

    FileWriteTool tool;

    @BeforeEach
    void setUp() {
        tool = new FileWriteTool(tempDir);
    }

    // --- Basic write ---

    @Test
    @DisplayName("writes content to a new file")
    void execute_newFile_writesContent() throws IOException {
        ToolResult result = tool.execute("{\"path\": \"output.txt\", \"content\": \"Hello, World!\"}");

        assertThat(result.isError()).isFalse();
        assertThat(Files.readString(tempDir.resolve("output.txt"))).isEqualTo("Hello, World!");
    }

    @Test
    @DisplayName("overwrites existing file with new content")
    void execute_existingFile_overwritesContent() throws IOException {
        Path file = tempDir.resolve("existing.txt");
        Files.writeString(file, "old content");

        ToolResult result = tool.execute("{\"path\": \"existing.txt\", \"content\": \"new content\"}");

        assertThat(result.isError()).isFalse();
        assertThat(Files.readString(file)).isEqualTo("new content");
    }

    @Test
    @DisplayName("creates parent directories if they do not exist")
    void execute_missingParentDirs_createsDirectories() throws IOException {
        ToolResult result = tool.execute(
                "{\"path\": \"a/b/c/file.txt\", \"content\": \"nested\"}");

        assertThat(result.isError()).isFalse();
        assertThat(Files.readString(tempDir.resolve("a/b/c/file.txt"))).isEqualTo("nested");
    }

    // --- Error cases ---

    @Test
    @DisplayName("path traversal is blocked")
    void execute_pathTraversal_blocked() {
        ToolResult result = tool.execute(
                "{\"path\": \"../../tmp/evil.txt\", \"content\": \"bad\"}");

        assertThat(result.isError()).isTrue();
        assertThat(result.output().toLowerCase()).satisfiesAnyOf(
                s -> assertThat(s).contains("traversal"),
                s -> assertThat(s).contains("denied"));
    }

    @Test
    @DisplayName("missing path field returns error")
    void execute_missingPath_returnsError() {
        ToolResult result = tool.execute("{\"content\": \"some content\"}");

        assertThat(result.isError()).isTrue();
        assertThat(result.output()).contains("path");
    }

    @Test
    @DisplayName("missing content field returns error")
    void execute_missingContent_returnsError() {
        ToolResult result = tool.execute("{\"path\": \"file.txt\"}");

        assertThat(result.isError()).isTrue();
        assertThat(result.output()).contains("content");
    }

    // --- Schema ---

    @Test
    @DisplayName("inputSchema() is valid JSON containing 'path' and 'content'")
    void inputSchema_isValidJson() {
        String schema = tool.inputSchema();
        assertThat(schema).isNotBlank();
        assertThat(schema).contains("path");
        assertThat(schema).contains("content");
    }
}
