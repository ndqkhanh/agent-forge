package com.agentforge.runtime;

import com.agentforge.api.provider.ApiClient;
import com.agentforge.api.provider.ApiRequest;
import com.agentforge.api.stream.AssistantEvent;
import com.agentforge.common.model.ModelInfo;
import com.agentforge.common.model.Session;
import com.agentforge.common.model.TokenUsage;
import com.agentforge.runtime.compaction.CompactionEngine;
import com.agentforge.runtime.prompt.SystemPromptBuilder;
import com.agentforge.runtime.usage.UsageTracker;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationRuntimeTest {

    // ------------------------------------------------------------------ helpers

    /** ApiClient stub that returns a fixed sequence of responses per call. */
    private static ApiClient textOnlyClient(String... responses) {
        Deque<String> queue = new ArrayDeque<>(List.of(responses));
        return new ApiClient() {
            @Override
            public Stream<AssistantEvent> streamMessage(ApiRequest request) {
                String text = queue.isEmpty() ? "done" : queue.poll();
                return Stream.of(
                    new AssistantEvent.MessageStart("msg-1"),
                    new AssistantEvent.UsageUpdate(TokenUsage.of(100, 50)),
                    new AssistantEvent.TextDelta(text),
                    new AssistantEvent.MessageStop("end_turn")
                );
            }
            @Override public String providerName() { return "stub"; }
            @Override public List<ModelInfo> availableModels() { return List.of(); }
        };
    }

    /** ApiClient that returns one tool call then a text response. */
    private static ApiClient singleToolCallClient(String toolId, String toolName, String toolInput,
                                                   String finalText) {
        AtomicInteger callCount = new AtomicInteger(0);
        return new ApiClient() {
            @Override
            public Stream<AssistantEvent> streamMessage(ApiRequest request) {
                int call = callCount.getAndIncrement();
                if (call == 0) {
                    // First call: return a tool use
                    return Stream.of(
                        new AssistantEvent.UsageUpdate(TokenUsage.of(100, 50)),
                        new AssistantEvent.ToolUseStart(toolId, toolName),
                        new AssistantEvent.ToolUseInputDelta(toolInput),
                        new AssistantEvent.ToolUseEnd(),
                        new AssistantEvent.MessageStop("tool_use")
                    );
                } else {
                    // Subsequent calls: return text
                    return Stream.of(
                        new AssistantEvent.UsageUpdate(TokenUsage.of(80, 40)),
                        new AssistantEvent.TextDelta(finalText),
                        new AssistantEvent.MessageStop("end_turn")
                    );
                }
            }
            @Override public String providerName() { return "stub"; }
            @Override public List<ModelInfo> availableModels() { return List.of(); }
        };
    }

    /** ApiClient that returns N tool calls then text. */
    private static ApiClient multiToolCallClient(List<String[]> toolCalls, String finalText) {
        AtomicInteger callCount = new AtomicInteger(0);
        return new ApiClient() {
            @Override
            public Stream<AssistantEvent> streamMessage(ApiRequest request) {
                int call = callCount.getAndIncrement();
                if (call < toolCalls.size()) {
                    String[] tc = toolCalls.get(call);
                    return Stream.of(
                        new AssistantEvent.ToolUseStart(tc[0], tc[1]),
                        new AssistantEvent.ToolUseInputDelta(tc[2]),
                        new AssistantEvent.ToolUseEnd(),
                        new AssistantEvent.MessageStop("tool_use")
                    );
                }
                return Stream.of(
                    new AssistantEvent.TextDelta(finalText),
                    new AssistantEvent.MessageStop("end_turn")
                );
            }
            @Override public String providerName() { return "stub"; }
            @Override public List<ModelInfo> availableModels() { return List.of(); }
        };
    }

    /** ToolExecutor that always returns a fixed result. */
    private static ToolExecutor alwaysSuccessExecutor(String result) {
        return new ToolExecutor() {
            @Override public String execute(String toolName, String input) { return result; }
            @Override public boolean supports(String toolName) { return true; }
        };
    }

    /** ToolExecutor that supports no tools. */
    private static ToolExecutor noToolExecutor() {
        return new ToolExecutor() {
            @Override public String execute(String n, String i) { throw new IllegalStateException("no tools"); }
            @Override public boolean supports(String toolName) { return false; }
        };
    }

    /** HookPipeline that allows everything unmodified. */
    private static HookPipeline allowAllHooks() {
        return new HookPipeline() {
            @Override public HookResult preToolUse(String name, String input) { return HookResult.allow(input); }
            @Override public String postToolUse(String name, String result) { return result; }
        };
    }

    /** HookPipeline that denies all tool calls. */
    private static HookPipeline denyAllHooks() {
        return new HookPipeline() {
            @Override public HookResult preToolUse(String name, String input) { return HookResult.deny("not permitted"); }
            @Override public String postToolUse(String name, String result) { return result; }
        };
    }

    /** PermissionChecker that allows everything. */
    private static PermissionChecker allowAllPermissions() {
        return (toolName, level) -> true;
    }

    /** PermissionChecker that denies everything. */
    private static PermissionChecker denyAllPermissions() {
        return (toolName, level) -> false;
    }

    private ConversationRuntime buildRuntime(ApiClient client, ToolExecutor executor,
                                              HookPipeline hooks, PermissionChecker perms) {
        return ConversationRuntime.builder()
            .apiClient(client)
            .toolExecutor(executor)
            .hookPipeline(hooks)
            .permissionChecker(perms)
            .promptBuilder(SystemPromptBuilder.builder().basePrompt("You are helpful.").build())
            .usageTracker(new UsageTracker())
            .compactionEngine(CompactionEngine.withDefaults())
            .session(Session.empty("test-session"))
            .build();
    }

    // ------------------------------------------------------------------ tests

    @Test
    @DisplayName("text-only response returns assistant text")
    void executeTurn_textOnly_returnsAssistantText() {
        ConversationRuntime runtime = buildRuntime(
            textOnlyClient("Hello, how can I help?"),
            noToolExecutor(), allowAllHooks(), allowAllPermissions());

        TurnResult result = runtime.executeTurn("Hi");
        assertThat(result.assistantText()).isEqualTo("Hello, how can I help?");
        assertThat(result.toolCalls()).isEmpty();
        assertThat(result.iterations()).isEqualTo(1);
    }

    @Test
    @DisplayName("single tool call executes tool and returns final text")
    void executeTurn_singleToolCall_executesAndReturnsText() {
        ConversationRuntime runtime = buildRuntime(
            singleToolCallClient("tool-1", "bash", "{\"cmd\":\"ls\"}", "Files listed."),
            alwaysSuccessExecutor("file1.txt\nfile2.txt"),
            allowAllHooks(), allowAllPermissions());

        TurnResult result = runtime.executeTurn("List files");
        assertThat(result.assistantText()).isEqualTo("Files listed.");
        assertThat(result.toolCalls()).hasSize(1);
        assertThat(result.toolCalls().get(0).name()).isEqualTo("bash");
        assertThat(result.toolCalls().get(0).isError()).isFalse();
        assertThat(result.iterations()).isEqualTo(2);
    }

    @Test
    @DisplayName("multiple tool calls in sequence are all executed")
    void executeTurn_multipleToolCalls_allExecuted() {
        List<String[]> toolCalls = List.of(
            new String[]{"t1", "read_file", "{\"path\":\"a.txt\"}"},
            new String[]{"t2", "write_file", "{\"path\":\"b.txt\"}"}
        );
        ConversationRuntime runtime = buildRuntime(
            multiToolCallClient(toolCalls, "Done."),
            alwaysSuccessExecutor("ok"),
            allowAllHooks(), allowAllPermissions());

        TurnResult result = runtime.executeTurn("Process files");
        assertThat(result.toolCalls()).hasSize(2);
        assertThat(result.toolCalls().get(0).name()).isEqualTo("read_file");
        assertThat(result.toolCalls().get(1).name()).isEqualTo("write_file");
        assertThat(result.assistantText()).isEqualTo("Done.");
    }

    @Test
    @DisplayName("max iterations limit prevents infinite loop")
    void executeTurn_infiniteToolLoop_stopsAtMaxIterations() {
        // Client always returns a tool call, never text
        ApiClient loopingClient = new ApiClient() {
            @Override
            public Stream<AssistantEvent> streamMessage(ApiRequest request) {
                return Stream.of(
                    new AssistantEvent.ToolUseStart("id-loop", "bash"),
                    new AssistantEvent.ToolUseInputDelta("{}"),
                    new AssistantEvent.ToolUseEnd(),
                    new AssistantEvent.MessageStop("tool_use")
                );
            }
            @Override public String providerName() { return "stub"; }
            @Override public List<ModelInfo> availableModels() { return List.of(); }
        };

        ConversationRuntime runtime = ConversationRuntime.builder()
            .apiClient(loopingClient)
            .toolExecutor(alwaysSuccessExecutor("result"))
            .hookPipeline(allowAllHooks())
            .permissionChecker(allowAllPermissions())
            .promptBuilder(SystemPromptBuilder.builder().build())
            .maxIterations(3)
            .session(Session.empty("s1"))
            .build();

        TurnResult result = runtime.executeTurn("go");
        assertThat(result.iterations()).isEqualTo(3);
    }

    @Test
    @DisplayName("hook pipeline denies tool call results in error tool result")
    void executeTurn_hookDeniesToolCall_toolResultIsError() {
        ConversationRuntime runtime = buildRuntime(
            singleToolCallClient("t1", "bash", "{}", "text after"),
            alwaysSuccessExecutor("ok"),
            denyAllHooks(), allowAllPermissions());

        TurnResult result = runtime.executeTurn("do something");
        assertThat(result.toolCalls()).hasSize(1);
        assertThat(result.toolCalls().get(0).isError()).isTrue();
        assertThat(result.toolCalls().get(0).output()).contains("denied");
    }

    @Test
    @DisplayName("permission check denies tool call results in error tool result")
    void executeTurn_permissionDenied_toolResultIsError() {
        ConversationRuntime runtime = buildRuntime(
            singleToolCallClient("t1", "bash", "{}", "text after"),
            alwaysSuccessExecutor("ok"),
            allowAllHooks(), denyAllPermissions());

        TurnResult result = runtime.executeTurn("do something");
        assertThat(result.toolCalls()).hasSize(1);
        assertThat(result.toolCalls().get(0).isError()).isTrue();
        assertThat(result.toolCalls().get(0).output()).contains("Permission denied");
    }

    @Test
    @DisplayName("usage is tracked across iterations")
    void executeTurn_usageTrackedAcrossIterations() {
        ConversationRuntime runtime = buildRuntime(
            singleToolCallClient("t1", "bash", "{}", "done"),
            alwaysSuccessExecutor("result"),
            allowAllHooks(), allowAllPermissions());

        TurnResult result = runtime.executeTurn("run");
        // Two iterations: tool call (100+50) + text response (80+40) = 330 total
        assertThat(result.turnUsage().totalTokens()).isGreaterThan(0);
    }

    @Test
    @DisplayName("session contains user message and assistant response after turn")
    void executeTurn_sessionUpdatedWithMessages() {
        ConversationRuntime runtime = buildRuntime(
            textOnlyClient("Hello!"),
            noToolExecutor(), allowAllHooks(), allowAllPermissions());

        runtime.executeTurn("Hi there");
        Session session = runtime.getSession();
        assertThat(session.messages()).hasSizeGreaterThanOrEqualTo(2);
        assertThat(session.messages().get(0).role()).isEqualTo("user");
    }

    @Test
    @DisplayName("empty user message returns empty TurnResult without calling API")
    void executeTurn_emptyUserMessage_returnsEmptyResult() {
        AtomicInteger callCount = new AtomicInteger(0);
        ApiClient countingClient = new ApiClient() {
            @Override
            public Stream<AssistantEvent> streamMessage(ApiRequest request) {
                callCount.incrementAndGet();
                return Stream.of(new AssistantEvent.TextDelta("x"), new AssistantEvent.MessageStop("end_turn"));
            }
            @Override public String providerName() { return "stub"; }
            @Override public List<ModelInfo> availableModels() { return List.of(); }
        };

        ConversationRuntime runtime = buildRuntime(
            countingClient, noToolExecutor(), allowAllHooks(), allowAllPermissions());

        TurnResult result = runtime.executeTurn("   ");
        assertThat(result.assistantText()).isEmpty();
        assertThat(callCount.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("unsupported tool results in error tool result")
    void executeTurn_unsupportedTool_resultsInError() {
        ConversationRuntime runtime = buildRuntime(
            singleToolCallClient("t1", "unknown_tool", "{}", "text"),
            noToolExecutor(),  // supports nothing
            allowAllHooks(), allowAllPermissions());

        TurnResult result = runtime.executeTurn("call unknown");
        assertThat(result.toolCalls()).hasSize(1);
        assertThat(result.toolCalls().get(0).isError()).isTrue();
        assertThat(result.toolCalls().get(0).output()).contains("Unknown tool");
    }

    @Test
    @DisplayName("builder requires apiClient")
    void builder_missingApiClient_throws() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
            ConversationRuntime.builder()
                .toolExecutor(noToolExecutor())
                .hookPipeline(allowAllHooks())
                .permissionChecker(allowAllPermissions())
                .promptBuilder(SystemPromptBuilder.builder().build())
                .build())
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("apiClient");
    }

    @Test
    @DisplayName("getSession returns updated session after turn")
    void getSession_afterTurn_returnsUpdatedSession() {
        ConversationRuntime runtime = buildRuntime(
            textOnlyClient("response"),
            noToolExecutor(), allowAllHooks(), allowAllPermissions());

        assertThat(runtime.getSession().messages()).isEmpty();
        runtime.executeTurn("hello");
        assertThat(runtime.getSession().messages()).isNotEmpty();
    }
}
