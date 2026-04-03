package com.agentforge.runtime;

import com.agentforge.api.provider.ApiClient;
import com.agentforge.api.provider.ApiRequest;
import com.agentforge.api.stream.AssistantEvent;
import com.agentforge.common.model.ContentBlock;
import com.agentforge.common.model.ConversationMessage;
import com.agentforge.common.model.Session;
import com.agentforge.common.model.TokenUsage;
import com.agentforge.common.model.ToolSchema;
import com.agentforge.runtime.compaction.CompactionEngine;
import com.agentforge.runtime.prompt.SystemPromptBuilder;
import com.agentforge.runtime.usage.UsageTracker;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * The core agent loop. Executes a single user turn, handling streaming,
 * tool calls, hook pipeline, permissions, usage tracking, and compaction.
 *
 * <p>Not thread-safe — use one instance per concurrent conversation.
 */
public final class ConversationRuntime {

    public static final int DEFAULT_MAX_ITERATIONS = 25;
    public static final String DEFAULT_MODEL = "claude-sonnet-4-5";

    private final ApiClient apiClient;
    private final ToolExecutor toolExecutor;
    private final HookPipeline hookPipeline;
    private final PermissionChecker permissionChecker;
    private final SystemPromptBuilder promptBuilder;
    private final UsageTracker usageTracker;
    private final CompactionEngine compactionEngine;
    private final int maxIterations;
    private final String model;
    private final List<ToolSchema> tools;
    private Session session;

    private ConversationRuntime(Builder builder) {
        this.apiClient = builder.apiClient;
        this.toolExecutor = builder.toolExecutor;
        this.hookPipeline = builder.hookPipeline;
        this.permissionChecker = builder.permissionChecker;
        this.promptBuilder = builder.promptBuilder;
        this.usageTracker = builder.usageTracker;
        this.compactionEngine = builder.compactionEngine;
        this.maxIterations = builder.maxIterations;
        this.model = builder.model;
        this.tools = List.copyOf(builder.tools);
        this.session = builder.session;
    }

    /**
     * Execute a single user turn (which may involve multiple tool-call iterations).
     *
     * @param userMessage the user's input text
     * @return the full result of this turn
     */
    public TurnResult executeTurn(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return new TurnResult("", List.of(), TokenUsage.ZERO, 0, false);
        }

        // Add user message to session
        session = session.addMessage(ConversationMessage.userText(userMessage));

        String systemPrompt = promptBuilder.build(tools);
        StringBuilder accumulatedText = new StringBuilder();
        List<TurnResult.ToolCall> allToolCalls = new ArrayList<>();
        TokenUsage turnUsage = TokenUsage.ZERO;
        int iterations = 0;
        boolean wasCompacted = false;

        // Agent loop — continue until text-only response or max iterations
        while (iterations < maxIterations) {
            iterations++;

            ApiRequest request = ApiRequest.builder()
                .model(model)
                .messages(session.messages())
                .systemPrompt(systemPrompt)
                .tools(tools)
                .build();

            // Collect streaming events from this iteration
            IterationResult iterResult = streamIteration(request);
            turnUsage = turnUsage.add(iterResult.usage());

            if (iterResult.usage().totalTokens() > 0) {
                usageTracker.track(model, iterResult.usage());
            }

            if (iterResult.toolCalls().isEmpty()) {
                // Text-only response — we're done
                String assistantText = iterResult.text();
                accumulatedText.append(assistantText);
                if (!assistantText.isEmpty()) {
                    session = session.addMessage(
                        new ConversationMessage("assistant",
                            List.of(new ContentBlock.Text(assistantText)),
                            iterResult.usage()));
                }
                break;
            }

            // Build assistant message with text + tool use blocks
            List<ContentBlock> assistantBlocks = new ArrayList<>();
            String iterText = iterResult.text();
            if (!iterText.isEmpty()) {
                assistantBlocks.add(new ContentBlock.Text(iterText));
                accumulatedText.append(iterText);
            }
            for (PendingToolCall pending : iterResult.toolCalls()) {
                assistantBlocks.add(new ContentBlock.ToolUse(pending.id(), pending.name(), pending.inputJson()));
            }
            session = session.addMessage(
                new ConversationMessage("assistant", assistantBlocks, iterResult.usage()));

            // Execute each tool call and collect results
            List<ContentBlock> toolResultBlocks = new ArrayList<>();
            for (PendingToolCall pending : iterResult.toolCalls()) {
                TurnResult.ToolCall toolCall = executeToolCall(pending);
                allToolCalls.add(toolCall);
                toolResultBlocks.add(new ContentBlock.ToolResult(
                    toolCall.id(), toolCall.output(), toolCall.isError()));
            }

            // Add tool results as a user message
            session = session.addMessage(
                ConversationMessage.of("user", toolResultBlocks));

            // Check compaction after adding results
            int contextWindow = 200_000; // default; could be derived from model info
            if (compactionEngine.shouldCompact(session, contextWindow)) {
                session = compactionEngine.compact(session, contextWindow);
                wasCompacted = true;
            }
        }

        return new TurnResult(accumulatedText.toString(), allToolCalls, turnUsage, iterations, wasCompacted);
    }

    /** Execute a single streaming API call and collect all events. */
    private IterationResult streamIteration(ApiRequest request) {
        StringBuilder text = new StringBuilder();
        List<PendingToolCall> toolCalls = new ArrayList<>();
        TokenUsage usage = TokenUsage.ZERO;

        // State for accumulating the current tool call
        String currentToolId = null;
        String currentToolName = null;
        StringBuilder currentToolInput = new StringBuilder();

        try (Stream<AssistantEvent> events = apiClient.streamMessage(request)) {
            for (AssistantEvent event : (Iterable<AssistantEvent>) events::iterator) {
                switch (event) {
                    case AssistantEvent.TextDelta delta -> text.append(delta.text());
                    case AssistantEvent.ToolUseStart start -> {
                        currentToolId = start.id();
                        currentToolName = start.name();
                        currentToolInput = new StringBuilder();
                    }
                    case AssistantEvent.ToolUseInputDelta delta -> {
                        if (currentToolInput != null) {
                            currentToolInput.append(delta.partialJson());
                        }
                    }
                    case AssistantEvent.ToolUseEnd ignored -> {
                        if (currentToolId != null && currentToolName != null) {
                            toolCalls.add(new PendingToolCall(
                                currentToolId, currentToolName,
                                currentToolInput != null ? currentToolInput.toString() : "{}"));
                            currentToolId = null;
                            currentToolName = null;
                            currentToolInput = null;
                        }
                    }
                    case AssistantEvent.UsageUpdate update -> usage = usage.add(update.usage());
                    case AssistantEvent.MessageStart ignored -> { /* no-op */ }
                    case AssistantEvent.MessageStop ignored -> { /* break handled by stream end */ }
                    case AssistantEvent.Error error ->
                        throw new RuntimeException("Stream error [" + error.type() + "]: " + error.message());
                }
            }
        }

        return new IterationResult(text.toString(), toolCalls, usage);
    }

    /** Execute a single tool call through the hook pipeline and permission checker. */
    private TurnResult.ToolCall executeToolCall(PendingToolCall pending) {
        // Pre-tool hook — may deny or modify input
        HookPipeline.HookResult hookResult = hookPipeline.preToolUse(pending.name(), pending.inputJson());
        if (!hookResult.allowed()) {
            String reason = hookResult.reason() != null ? hookResult.reason() : "Denied by hook pipeline";
            return new TurnResult.ToolCall(pending.id(), pending.name(), pending.inputJson(),
                "Tool call denied: " + reason, true);
        }

        String effectiveInput = hookResult.modifiedInput() != null ? hookResult.modifiedInput() : pending.inputJson();

        // Permission check
        boolean permitted = permissionChecker.isAllowed(pending.name(), PermissionChecker.PermissionLevel.WORKSPACE_WRITE);
        if (!permitted) {
            return new TurnResult.ToolCall(pending.id(), pending.name(), effectiveInput,
                "Permission denied for tool: " + pending.name(), true);
        }

        // Execute tool
        if (!toolExecutor.supports(pending.name())) {
            return new TurnResult.ToolCall(pending.id(), pending.name(), effectiveInput,
                "Unknown tool: " + pending.name(), true);
        }

        try {
            String rawResult = toolExecutor.execute(pending.name(), effectiveInput);
            // Post-tool hook — may transform result
            String finalResult = hookPipeline.postToolUse(pending.name(), rawResult);
            return new TurnResult.ToolCall(pending.id(), pending.name(), effectiveInput,
                finalResult != null ? finalResult : rawResult, false);
        } catch (Exception e) {
            return new TurnResult.ToolCall(pending.id(), pending.name(), effectiveInput,
                "Tool execution error: " + e.getMessage(), true);
        }
    }

    public Session getSession() {
        return session;
    }

    public UsageTracker getUsageTracker() {
        return usageTracker;
    }

    public static Builder builder() {
        return new Builder();
    }

    // Internal records for streaming state
    private record PendingToolCall(String id, String name, String inputJson) {}

    private record IterationResult(String text, List<PendingToolCall> toolCalls, TokenUsage usage) {}

    public static final class Builder {
        private ApiClient apiClient;
        private ToolExecutor toolExecutor;
        private HookPipeline hookPipeline;
        private PermissionChecker permissionChecker;
        private SystemPromptBuilder promptBuilder;
        private UsageTracker usageTracker;
        private CompactionEngine compactionEngine;
        private int maxIterations = DEFAULT_MAX_ITERATIONS;
        private String model = DEFAULT_MODEL;
        private final List<ToolSchema> tools = new ArrayList<>();
        private Session session;

        private Builder() {}

        public Builder apiClient(ApiClient apiClient) {
            this.apiClient = apiClient;
            return this;
        }

        public Builder toolExecutor(ToolExecutor toolExecutor) {
            this.toolExecutor = toolExecutor;
            return this;
        }

        public Builder hookPipeline(HookPipeline hookPipeline) {
            this.hookPipeline = hookPipeline;
            return this;
        }

        public Builder permissionChecker(PermissionChecker permissionChecker) {
            this.permissionChecker = permissionChecker;
            return this;
        }

        public Builder promptBuilder(SystemPromptBuilder promptBuilder) {
            this.promptBuilder = promptBuilder;
            return this;
        }

        public Builder usageTracker(UsageTracker usageTracker) {
            this.usageTracker = usageTracker;
            return this;
        }

        public Builder compactionEngine(CompactionEngine compactionEngine) {
            this.compactionEngine = compactionEngine;
            return this;
        }

        public Builder maxIterations(int maxIterations) {
            this.maxIterations = maxIterations;
            return this;
        }

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder tools(List<ToolSchema> tools) {
            this.tools.clear();
            this.tools.addAll(tools);
            return this;
        }

        public Builder session(Session session) {
            this.session = session;
            return this;
        }

        public ConversationRuntime build() {
            if (apiClient == null) throw new IllegalStateException("apiClient is required");
            if (toolExecutor == null) throw new IllegalStateException("toolExecutor is required");
            if (hookPipeline == null) throw new IllegalStateException("hookPipeline is required");
            if (permissionChecker == null) throw new IllegalStateException("permissionChecker is required");
            if (promptBuilder == null) throw new IllegalStateException("promptBuilder is required");
            if (usageTracker == null) usageTracker = new UsageTracker();
            if (compactionEngine == null) compactionEngine = CompactionEngine.withDefaults();
            if (session == null) session = Session.empty(java.util.UUID.randomUUID().toString());
            return new ConversationRuntime(this);
        }
    }
}
