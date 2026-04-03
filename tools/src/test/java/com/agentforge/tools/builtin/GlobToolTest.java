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

@DisplayName("GlobTool")
class GlobToolTest {

    @TempDir
    Path tempDir;

    GlobTool tool;

    @BeforeEach
    void setUp() {
        tool = new GlobTool(tempDir);
    }

    // --- Basic glob ---

    @Test
    @DisplayName("finds files matching simple extension pattern")
    void execute_extensionPattern_findsMatches() throws IOException {
        Files.writeString(tempDir.resolve("A.java"), "class A {}");
        Files.writeString(tempDir.resolve("B.java"), "class B {}");
        Files.writeString(tempDir.resolve("notes.txt"), "notes");

        ToolResult result = tool.execute("{\"pattern\": \"*.java\"}");

        assertThat(result.isError()).isFalse();
        assertThat(result.output()).contains("A.java");
        assertThat(result.output()).contains("B.java");
        assertThat(result.output()).doesNotContain("notes.txt");
    }

    @Test
    @DisplayName("recursive glob (**/*.java) finds files in subdirectories")
    void execute_recursiveGlob_findsNestedFiles() throws IOException {
        Path subDir = tempDir.resolve("src/main");
        Files.createDirectories(subDir);
        Files.writeString(subDir.resolve("Main.java"), "class Main {}");
        Files.writeString(tempDir.resolve("Root.java"), "class Root {}");

        ToolResult result = tool.execute("{\"pattern\": \"**/*.java\"}");

        assertThat(result.isError()).isFalse();
        assertThat(result.output()).contains("Main.java");
        assertThat(result.output()).contains("Root.java");
    }

    @Test
    @DisplayName("no matches returns informational success message")
    void execute_noMatches_returnsEmptySuccess() throws IOException {
        Files.writeString(tempDir.resolve("readme.txt"), "text");

        ToolResult result = tool.execute("{\"pattern\": \"*.xyz\"}");

        assertThat(result.isError()).isFalse();
        assertThat(result.output()).containsIgnoringCase("no files");
    }

    @Test
    @DisplayName("results are sorted alphabetically")
    void execute_results_areSorted() throws IOException {
        Files.writeString(tempDir.resolve("c.txt"), "");
        Files.writeString(tempDir.resolve("a.txt"), "");
        Files.writeString(tempDir.resolve("b.txt"), "");

        ToolResult result = tool.execute("{\"pattern\": \"*.txt\"}");

        assertThat(result.isError()).isFalse();
        String[] lines = result.output().split("\n");
        assertThat(lines).isSortedAccordingTo(String::compareTo);
    }

    @Test
    @DisplayName("missing pattern field returns error")
    void execute_missingPattern_returnsError() {
        ToolResult result = tool.execute("{}");

        assertThat(result.isError()).isTrue();
        assertThat(result.output()).contains("pattern");
    }

    // --- Schema ---

    @Test
    @DisplayName("inputSchema() contains 'pattern' field")
    void inputSchema_containsPattern() {
        String schema = tool.inputSchema();
        assertThat(schema).isNotBlank();
        assertThat(schema).contains("pattern");
    }
}
