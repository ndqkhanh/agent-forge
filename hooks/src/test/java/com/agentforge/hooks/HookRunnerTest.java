package com.agentforge.hooks;

import com.agentforge.hooks.HookResult.HookOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for HookRunner using real shell commands (macOS/Linux compatible).
 */
class HookRunnerTest {

    private final HookRunner runner = new HookRunner(5_000L);

    private HookDefinition preHook(String command) {
        return new HookDefinition("test", HookType.PRE_TOOL_USE, command, List.of("*"));
    }

    @Test
    @DisplayName("exit 0 produces ALLOW outcome")
    void exitZero_producesAllow() {
        HookResult result = runner.run(preHook("exit 0"), "bash", "{}");
        assertThat(result.outcome()).isEqualTo(HookOutcome.ALLOW);
    }

    @Test
    @DisplayName("exit 2 produces DENY outcome with stdout as reason")
    void exitTwo_producesDenyWithReason() {
        HookResult result = runner.run(preHook("echo 'secret detected'; exit 2"), "bash", "{}");
        assertThat(result.outcome()).isEqualTo(HookOutcome.DENY);
        assertThat(result.message()).contains("secret detected");
    }

    @Test
    @DisplayName("exit 1 produces ERROR outcome")
    void exitOne_producesError() {
        HookResult result = runner.run(preHook("exit 1"), "bash", "{}");
        assertThat(result.outcome()).isEqualTo(HookOutcome.ERROR);
    }

    @Test
    @DisplayName("exit 0 with stdout sets modifiedInput")
    void exitZeroWithStdout_setsModifiedInput() {
        HookResult result = runner.run(preHook("echo '{\"modified\":true}'"), "bash", "{}");
        assertThat(result.outcome()).isEqualTo(HookOutcome.ALLOW);
        assertThat(result.modifiedInput()).isEqualTo("{\"modified\":true}");
    }

    @Test
    @DisplayName("timeout produces ERROR outcome")
    void timeout_producesError() {
        HookRunner slowRunner = new HookRunner(100L);
        HookResult result = slowRunner.run(preHook("sleep 5"), "bash", "{}");
        assertThat(result.outcome()).isEqualTo(HookOutcome.ERROR);
        assertThat(result.message()).containsIgnoringCase("timed out");
    }

    @Test
    @DisplayName("command not found produces ERROR outcome")
    void commandNotFound_producesError() {
        HookResult result = runner.run(preHook("this_command_does_not_exist_xyz_abc"), "bash", "{}");
        assertThat(result.outcome()).isEqualTo(HookOutcome.ERROR);
    }

    @Test
    @DisplayName("empty command produces ERROR outcome")
    void emptyCommand_producesError() {
        HookResult result = runner.run(preHook(""), "bash", "{}");
        assertThat(result.outcome()).isEqualTo(HookOutcome.ERROR);
    }

    @Test
    @DisplayName("stdin contains JSON with tool, input, and type fields")
    void stdin_containsCorrectJsonPayload() {
        // Use cat to echo stdin back to stdout, then grep for expected fields
        String command = "input=$(cat); echo \"$input\"";
        HookResult result = runner.run(preHook(command), "bash", "{\"key\":\"val\"}");
        assertThat(result.outcome()).isEqualTo(HookOutcome.ALLOW);
        assertThat(result.modifiedInput()).contains("\"tool\"");
        assertThat(result.modifiedInput()).contains("\"bash\"");
        assertThat(result.modifiedInput()).contains("\"type\"");
        assertThat(result.modifiedInput()).contains("pre_tool_use");
    }

    @Test
    @DisplayName("exit 2 with empty stdout uses default deny message")
    void exitTwo_emptyStdout_usesDefaultDenyMessage() {
        HookResult result = runner.run(preHook("exit 2"), "bash", "{}");
        assertThat(result.outcome()).isEqualTo(HookOutcome.DENY);
        assertThat(result.message()).isNotBlank();
    }
}
