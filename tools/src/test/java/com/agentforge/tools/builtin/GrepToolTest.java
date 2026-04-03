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

@DisplayName("GrepTool")
class GrepToolTest {

    @TempDir
    Path tempDir;

    GrepTool tool;

    @BeforeEach
    void setUp() {
        tool = new GrepTool(tempDir);
    }

    // --- Matching ---

    @Test
    @DisplayName("finds lines matching a literal pattern")
    void execute_literalPattern_findsMatches() throws IOException {
        Path file = tempDir.resolve("code.txt");
        Files.writeString(file, "TODO: fix this\nDone\nTODO: fix that\n");

        ToolResult result = tool.execute("{\"pattern\": \"TODO\"}");

        assertThat(result.isError()).isFalse();
        assertThat(result.output()).contains("TODO: fix this");
        assertThat(result.output()).contains("TODO: fix that");
        assertThat(result.output()).doesNotContain("Done");
    }

    @Test
    @DisplayName("supports regex pattern matching")
    void execute_regexPattern_matchesCorrectly() throws IOException {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "error: something\ninfo: ok\nwarn: careful\nerror: again\n");

        ToolResult result = tool.execute("{\"pattern\": \"^error:\"}");

        assertThat(result.isError()).isFalse();
        assertThat(result.output()).contains("error: something");
        assertThat(result.output()).contains("error: again");
        assertThat(result.output()).doesNotContain("info:");
        assertThat(result.output()).doesNotContain("warn:");
    }

    @Test
    @DisplayName("no matches returns informational success message")
    void execute_noMatches_returnsEmptySuccess() throws IOException {
        Path file = tempDir.resolve("nothing.txt");
        Files.writeString(file, "line one\nline two\n");

        ToolResult result = tool.execute("{\"pattern\": \"ZZZNOMATCH\"}");

        assertThat(result.isError()).isFalse();
        assertThat(result.output()).containsIgnoringCase("no matches");
    }

    @Test
    @DisplayName("include filter restricts files searched")
    void execute_includeFilter_restrictsFiles() throws IOException {
        Files.writeString(tempDir.resolve("Main.java"), "public class Main {}\n");
        Files.writeString(tempDir.resolve("README.md"), "public info here\n");

        ToolResult result = tool.execute("{\"pattern\": \"public\", \"include\": \"*.java\"}");

        assertThat(result.isError()).isFalse();
        assertThat(result.output()).contains("Main.java");
        assertThat(result.output()).doesNotContain("README.md");
    }

    @Test
    @DisplayName("output format is file:line: content")
    void execute_outputFormat_includesFileAndLineNumber() throws IOException {
        Path file = tempDir.resolve("sample.txt");
        Files.writeString(file, "alpha\nbeta\ngamma\n");

        ToolResult result = tool.execute("{\"pattern\": \"beta\"}");

        assertThat(result.isError()).isFalse();
        // Should contain "sample.txt:2: beta"
        assertThat(result.output()).contains("sample.txt");
        assertThat(result.output()).contains("2");
        assertThat(result.output()).contains("beta");
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
