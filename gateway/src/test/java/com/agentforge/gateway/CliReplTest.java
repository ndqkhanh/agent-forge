package com.agentforge.gateway;

import com.agentforge.api.provider.ApiClient;
import com.agentforge.api.provider.ApiRequest;
import com.agentforge.api.stream.AssistantEvent;
import com.agentforge.common.model.ModelInfo;
import com.agentforge.common.model.Session;
import com.agentforge.runtime.ConversationRuntime;
import com.agentforge.runtime.HookPipeline;
import com.agentforge.runtime.PermissionChecker;
import com.agentforge.runtime.ToolExecutor;
import com.agentforge.runtime.prompt.SystemPromptBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CliReplTest {

    private ConversationRuntime runtime;
    private ByteArrayOutputStream outBytes;
    private PrintStream out;

    @BeforeEach
    void setUp() {
        runtime = buildRuntime("test-repl-session");
        outBytes = new ByteArrayOutputStream();
        out = new PrintStream(outBytes, true, StandardCharsets.UTF_8);
    }

    // -------------------------------------------------------------------------
    // Constructor validation
    // -------------------------------------------------------------------------

    @Test
    void constructor_nullRuntime_throwsIllegalArgument() {
        assertThatThrownBy(() -> new CliRepl(null, System.in, System.out))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("runtime");
    }

    @Test
    void constructor_nullInputStream_throwsIllegalArgument() {
        assertThatThrownBy(() -> new CliRepl(runtime, null, System.out))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("in");
    }

    @Test
    void constructor_nullOutputStream_throwsIllegalArgument() {
        assertThatThrownBy(() -> new CliRepl(runtime, System.in, null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("out");
    }

    // -------------------------------------------------------------------------
    // parseCommand delegation
    // -------------------------------------------------------------------------

    @Test
    void parseCommand_helpInput_returnsHelp() {
        Optional<SlashCommand> cmd = CliRepl.parseCommand("/help");
        assertThat(cmd).isPresent();
        assertThat(cmd.get()).isInstanceOf(SlashCommand.Help.class);
    }

    @Test
    void parseCommand_quitInput_returnsQuit() {
        Optional<SlashCommand> cmd = CliRepl.parseCommand("/quit");
        assertThat(cmd).isPresent();
        assertThat(cmd.get()).isInstanceOf(SlashCommand.Quit.class);
    }

    @Test
    void parseCommand_regularText_returnsEmpty() {
        assertThat(CliRepl.parseCommand("hello there")).isEmpty();
    }

    @Test
    void parseCommand_null_returnsEmpty() {
        assertThat(CliRepl.parseCommand(null)).isEmpty();
    }

    // -------------------------------------------------------------------------
    // REPL slash command handling via start()
    // -------------------------------------------------------------------------

    @Test
    void start_helpCommand_printsHelpText() {
        CliRepl repl = replWithInput("/help\n/quit\n");
        repl.start();

        String output = outBytes.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("/help");
        assertThat(output).contains("/quit");
    }

    @Test
    void start_quitCommand_exits() {
        CliRepl repl = replWithInput("/quit\n");
        repl.start();

        String output = outBytes.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("Goodbye");
        assertThat(repl.isRunning()).isFalse();
    }

    @Test
    void start_statusCommand_showsSessionInfo() {
        CliRepl repl = replWithInput("/status\n/quit\n");
        repl.start();

        String output = outBytes.toString(StandardCharsets.UTF_8);
        assertThat(output).containsIgnoringCase("session");
        assertThat(output).containsIgnoringCase("model");
    }

    @Test
    void start_clearCommand_printsAcknowledgement() {
        CliRepl repl = replWithInput("/clear\n/quit\n");
        repl.start();

        String output = outBytes.toString(StandardCharsets.UTF_8);
        assertThat(output).containsIgnoringCase("clear");
    }

    @Test
    void start_compactCommand_printsAcknowledgement() {
        CliRepl repl = replWithInput("/compact\n/quit\n");
        repl.start();

        String output = outBytes.toString(StandardCharsets.UTF_8);
        assertThat(output).containsIgnoringCase("compact");
    }

    @Test
    void start_modelCommandNoArg_showsCurrentModel() {
        CliRepl repl = replWithInput("/model\n/quit\n");
        repl.start();

        String output = outBytes.toString(StandardCharsets.UTF_8);
        assertThat(output).containsIgnoringCase("model");
    }

    @Test
    void start_modelCommandWithArg_setsModel() {
        CliRepl repl = replWithInput("/model claude-haiku-4-6\n/model\n/quit\n");
        repl.start();

        String output = outBytes.toString(StandardCharsets.UTF_8);
        assertThat(output).contains("claude-haiku-4-6");
    }

    @Test
    void start_emptyLine_skipped() {
        CliRepl repl = replWithInput("\n\n/quit\n");
        repl.start();

        // Should complete without error
        assertThat(repl.isRunning()).isFalse();
    }

    @Test
    void start_eofInput_exits() {
        // Empty input simulates immediate EOF
        CliRepl repl = replWithInput("");
        repl.start();

        assertThat(repl.isRunning()).isFalse();
    }

    @Test
    void start_userMessage_callsRuntime() {
        CliRepl repl = replWithInput("hello\n/quit\n");
        repl.start();

        String output = outBytes.toString(StandardCharsets.UTF_8);
        // The noop runtime returns "hello" as text delta
        assertThat(output).contains("Assistant");
    }

    @Test
    void start_greetingPrinted_onStart() {
        CliRepl repl = replWithInput("/quit\n");
        repl.start();

        String output = outBytes.toString(StandardCharsets.UTF_8);
        assertThat(output).containsIgnoringCase("agentforge");
    }

    // -------------------------------------------------------------------------
    // stop()
    // -------------------------------------------------------------------------

    @Test
    void stop_setsRunningFalse() {
        CliRepl repl = replWithInput(""); // EOF so it won't block
        repl.stop();
        assertThat(repl.isRunning()).isFalse();
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private CliRepl replWithInput(String input) {
        ByteArrayInputStream in = new ByteArrayInputStream(input.getBytes(StandardCharsets.UTF_8));
        return new CliRepl(runtime, in, out);
    }

    private static ConversationRuntime buildRuntime(String sessionId) {
        ApiClient echoClient = new ApiClient() {
            @Override
            public Stream<AssistantEvent> streamMessage(ApiRequest request) {
                // Echo back the last user message as assistant text
                String lastMsg = request.messages().isEmpty() ? "hello" :
                    request.messages().getLast().textContent();
                return Stream.of(
                    new AssistantEvent.TextDelta(lastMsg),
                    new AssistantEvent.MessageStop("end_turn")
                );
            }
            @Override public String providerName() { return "echo"; }
            @Override public List<ModelInfo> availableModels() { return List.of(); }
        };

        ToolExecutor noopTools = new ToolExecutor() {
            @Override public String execute(String n, String i) { return "{}"; }
            @Override public boolean supports(String n) { return false; }
        };

        HookPipeline noopHooks = new HookPipeline() {
            @Override public HookResult preToolUse(String n, String i) {
                return HookResult.allow(i);
            }
            @Override public String postToolUse(String n, String r) { return r; }
        };

        SystemPromptBuilder promptBuilder = SystemPromptBuilder.builder()
            .basePrompt("test")
            .build();

        return ConversationRuntime.builder()
            .session(Session.empty(sessionId))
            .apiClient(echoClient)
            .toolExecutor(noopTools)
            .hookPipeline(noopHooks)
            .permissionChecker((n, l) -> true)
            .promptBuilder(promptBuilder)
            .build();
    }
}
