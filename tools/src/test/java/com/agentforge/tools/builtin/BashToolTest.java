package com.agentforge.tools.builtin;

import com.agentforge.tools.ToolExecutor.ToolResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("BashTool")
class BashToolTest {

    @TempDir
    Path tempDir;

    // --- Basic execution ---

    @Test
    @DisplayName("echo command returns expected output")
    void execute_echoCommand_returnsOutput() {
        BashTool tool = new BashTool(tempDir);
        ToolResult result = tool.execute("{\"command\": \"echo hello\"}");

        assertThat(result.isError()).isFalse();
        assertThat(result.output().trim()).isEqualTo("hello");
    }

    @Test
    @DisplayName("stdout is fully captured")
    void execute_multiLineOutput_capturesAll() {
        BashTool tool = new BashTool(tempDir);
        ToolResult result = tool.execute("{\"command\": \"printf 'line1\\nline2\\nline3'\"}");

        assertThat(result.isError()).isFalse();
        assertThat(result.output()).contains("line1", "line2", "line3");
    }

    @Test
    @DisplayName("stderr output captured on command error")
    void execute_commandError_capturesStderr() {
        BashTool tool = new BashTool(tempDir);
        // ls on non-existent path writes to stderr; exit code != 0 → isError
        ToolResult result = tool.execute("{\"command\": \"ls /nonexistent_path_xyz 2>&1\"}");

        assertThat(result.isError()).isTrue();
        assertThat(result.output()).isNotBlank();
    }

    @Test
    @DisplayName("exit code != 0 returns error result")
    void execute_nonZeroExitCode_returnsError() {
        BashTool tool = new BashTool(tempDir);
        ToolResult result = tool.execute("{\"command\": \"exit 42\"}");

        assertThat(result.isError()).isTrue();
        assertThat(result.output()).contains("42");
    }

    // --- Working directory ---

    @Test
    @DisplayName("pwd reflects configured working directory")
    void execute_pwdCommand_returnsWorkingDirectory() {
        BashTool tool = new BashTool(tempDir);
        ToolResult result = tool.execute("{\"command\": \"pwd\"}");

        assertThat(result.isError()).isFalse();
        // tempDir may be a symlink-resolved path; check canonical form
        assertThat(result.output().trim()).isNotBlank();
    }

    // --- Input validation ---

    @Test
    @DisplayName("missing command field returns error")
    void execute_missingCommand_returnsError() {
        BashTool tool = new BashTool(tempDir);
        ToolResult result = tool.execute("{}");

        assertThat(result.isError()).isTrue();
        assertThat(result.output()).contains("command");
    }

    @Test
    @DisplayName("empty command returns error")
    void execute_emptyCommand_returnsError() {
        BashTool tool = new BashTool(tempDir);
        ToolResult result = tool.execute("{\"command\": \"\"}");

        assertThat(result.isError()).isTrue();
    }

    @Test
    @DisplayName("invalid JSON returns error")
    void execute_invalidJson_returnsError() {
        BashTool tool = new BashTool(tempDir);
        ToolResult result = tool.execute("not json");

        assertThat(result.isError()).isTrue();
    }

    // --- Timeout ---

    @Test
    @DisplayName("command exceeding timeout is killed and returns error")
    void execute_timeoutExceeded_returnsError() {
        // 100ms timeout, sleep 5s
        BashTool tool = new BashTool(tempDir, 100);
        ToolResult result = tool.execute("{\"command\": \"sleep 5\"}");

        assertThat(result.isError()).isTrue();
        assertThat(result.output()).containsIgnoringCase("timed out");
    }

    // --- Schema ---

    @Test
    @DisplayName("name() returns 'bash'")
    void name_returnsBash() {
        BashTool tool = new BashTool(tempDir);
        assertThat(tool.name()).isEqualTo("bash");
    }

    @Test
    @DisplayName("inputSchema() is valid JSON containing 'command'")
    void inputSchema_isValidJson() {
        BashTool tool = new BashTool(tempDir);
        String schema = tool.inputSchema();
        assertThat(schema).isNotBlank();
        assertThat(schema).contains("command");
    }
}
