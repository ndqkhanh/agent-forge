package com.agentforge.gateway;

import com.agentforge.config.loader.AgentForgeConfig;
import com.agentforge.config.loader.ConfigLoader;
import com.agentforge.hooks.HookPipeline;
import com.agentforge.runtime.ConversationRuntime;
import com.agentforge.runtime.PermissionChecker;
import com.agentforge.runtime.ToolExecutor;
import com.agentforge.runtime.prompt.SystemPromptBuilder;
import com.agentforge.tools.registry.ToolRegistry;

import java.io.IOException;
import java.nio.file.Path;

/**
 * Builder that wires together all modules and produces a ready-to-start ForgeServer.
 *
 * <p>Usage:
 * <pre>{@code
 * ForgeServer server = ForgeBuilder.builder()
 *     .port(8080)
 *     .userConfig(Path.of("~/.agentforge/config.json"))
 *     .toolRegistry(registry)
 *     .build()
 *     .buildServer();
 * server.start();
 * }</pre>
 */
public final class ForgeBuilder {

    private final int port;
    private final ConfigLoader configLoader;
    private final ToolRegistry toolRegistry;
    private final HookPipeline hookPipeline;
    private final String basePrompt;

    private ForgeBuilder(Builder builder) {
        this.port = builder.port;
        this.configLoader = builder.configLoader;
        this.toolRegistry = builder.toolRegistry;
        this.hookPipeline = builder.hookPipeline;
        this.basePrompt = builder.basePrompt;
    }

    /**
     * Wire everything together and build a ForgeServer ready to start.
     *
     * @return a configured ForgeServer
     * @throws IOException if the HTTP server cannot be created
     */
    public ForgeServer buildServer() throws IOException {
        // 1. Load configuration
        AgentForgeConfig config = configLoader.load();

        // 2. Resolve HookPipeline (hooks module)
        HookPipeline resolvedHooks = hookPipeline != null ? hookPipeline : HookPipeline.empty();

        // 3. Build HookPipeline adapter (bridges hooks.HookPipeline -> runtime.HookPipeline)
        com.agentforge.runtime.HookPipeline runtimeHooks = new HookPipelineAdapter(resolvedHooks);

        // 4. Build ToolExecutor adapter (bridges tools.ToolRegistry -> runtime.ToolExecutor)
        ToolRegistry resolvedRegistry = toolRegistry != null ? toolRegistry : ToolRegistry.builder().build();
        ToolExecutor toolExecutor = new ToolRegistryAdapter(resolvedRegistry);

        // 5. Build PermissionChecker from config
        PermissionChecker permissionChecker = new ConfigPermissionChecker(config);

        // 6. Build SystemPromptBuilder
        SystemPromptBuilder promptBuilder = SystemPromptBuilder.builder()
            .basePrompt(basePrompt)
            .instructionFiles(config.instructionFiles())
            .build();

        // 7. Build SessionManager and RuntimeFactory
        SessionManager sessionManager = new SessionManager();
        RuntimeFactory runtimeFactory = new RuntimeFactory(
            resolvedRegistry, runtimeHooks, toolExecutor, permissionChecker, promptBuilder, config);

        // 8. Build and return the server
        return new ForgeServer(port, sessionManager, runtimeFactory, config);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private int port = 8080;
        private ConfigLoader configLoader = ConfigLoader.builder().build();
        private ToolRegistry toolRegistry;
        private HookPipeline hookPipeline;
        private String basePrompt = "You are a helpful AI assistant.";

        private Builder() {}

        public Builder port(int port) {
            if (port < 1 || port > 65535) throw new IllegalArgumentException("port must be 1-65535");
            this.port = port;
            return this;
        }

        public Builder userConfig(Path path) {
            this.configLoader = ConfigLoader.builder().userConfig(path).build();
            return this;
        }

        public Builder configLoader(ConfigLoader configLoader) {
            if (configLoader == null) throw new IllegalArgumentException("configLoader must not be null");
            this.configLoader = configLoader;
            return this;
        }

        public Builder toolRegistry(ToolRegistry toolRegistry) {
            this.toolRegistry = toolRegistry;
            return this;
        }

        public Builder hookPipeline(HookPipeline hookPipeline) {
            this.hookPipeline = hookPipeline;
            return this;
        }

        public Builder basePrompt(String basePrompt) {
            this.basePrompt = basePrompt != null ? basePrompt : "";
            return this;
        }

        public ForgeBuilder build() {
            return new ForgeBuilder(this);
        }
    }

    // -------------------------------------------------------------------------
    // Internal adapter: tools.ToolRegistry -> runtime.ToolExecutor
    // (runtime.ToolExecutor.execute returns String; tools.ToolExecutor.execute returns ToolResult)
    // -------------------------------------------------------------------------

    static final class ToolRegistryAdapter implements ToolExecutor {
        private final ToolRegistry registry;

        ToolRegistryAdapter(ToolRegistry registry) {
            this.registry = registry;
        }

        @Override
        public String execute(String toolName, String inputJson) {
            com.agentforge.tools.ToolExecutor.ToolResult result = registry.execute(toolName, inputJson);
            return result.output();
        }

        @Override
        public boolean supports(String toolName) {
            return registry.supports(toolName);
        }
    }

    // -------------------------------------------------------------------------
    // Internal adapter: hooks.HookPipeline -> runtime.HookPipeline
    // -------------------------------------------------------------------------

    static final class HookPipelineAdapter implements com.agentforge.runtime.HookPipeline {
        private final HookPipeline pipeline;

        HookPipelineAdapter(HookPipeline pipeline) {
            this.pipeline = pipeline;
        }

        @Override
        public com.agentforge.runtime.HookPipeline.HookResult preToolUse(String toolName, String inputJson) {
            com.agentforge.hooks.HookResult result = pipeline.executePreToolUse(toolName, inputJson);
            return switch (result.outcome()) {
                case ALLOW -> com.agentforge.runtime.HookPipeline.HookResult.allow(
                    result.modifiedInput() != null ? result.modifiedInput() : inputJson);
                case DENY  -> com.agentforge.runtime.HookPipeline.HookResult.deny(result.message());
                case ERROR -> com.agentforge.runtime.HookPipeline.HookResult.deny(
                    "Hook error: " + result.message());
            };
        }

        @Override
        public String postToolUse(String toolName, String result) {
            com.agentforge.hooks.HookResult hookResult = pipeline.executePostToolUse(toolName, result);
            if (hookResult.modifiedInput() != null) return hookResult.modifiedInput();
            return result;
        }
    }

    // -------------------------------------------------------------------------
    // Internal: config-based PermissionChecker
    // -------------------------------------------------------------------------

    static final class ConfigPermissionChecker implements PermissionChecker {
        private final AgentForgeConfig config;

        ConfigPermissionChecker(AgentForgeConfig config) {
            this.config = config;
        }

        @Override
        public boolean isAllowed(String toolName, PermissionLevel required) {
            com.agentforge.config.permission.PermissionLevel configLevel = config.permissionLevel();
            PermissionLevel effective = switch (configLevel) {
                case READ_ONLY          -> PermissionLevel.READ_ONLY;
                case WORKSPACE_WRITE    -> PermissionLevel.WORKSPACE_WRITE;
                case DANGER_FULL_ACCESS -> PermissionLevel.DANGER_FULL_ACCESS;
            };
            return effective.ordinal() >= required.ordinal();
        }
    }

    // -------------------------------------------------------------------------
    // Internal: factory for ConversationRuntime instances (one per session)
    // -------------------------------------------------------------------------

    static final class RuntimeFactory {
        private final ToolRegistry registry;
        private final com.agentforge.runtime.HookPipeline hookPipeline;
        private final ToolExecutor toolExecutor;
        private final PermissionChecker permissionChecker;
        private final SystemPromptBuilder promptBuilder;
        private final AgentForgeConfig config;

        RuntimeFactory(
            ToolRegistry registry,
            com.agentforge.runtime.HookPipeline hookPipeline,
            ToolExecutor toolExecutor,
            PermissionChecker permissionChecker,
            SystemPromptBuilder promptBuilder,
            AgentForgeConfig config) {
            this.registry = registry;
            this.hookPipeline = hookPipeline;
            this.toolExecutor = toolExecutor;
            this.permissionChecker = permissionChecker;
            this.promptBuilder = promptBuilder;
            this.config = config;
        }

        ConversationRuntime create(String sessionId) {
            return ConversationRuntime.builder()
                .session(com.agentforge.common.model.Session.empty(sessionId))
                .toolExecutor(toolExecutor)
                .hookPipeline(hookPipeline)
                .permissionChecker(permissionChecker)
                .promptBuilder(promptBuilder)
                .tools(registry.allSchemas())
                .model(config.model())
                .maxIterations(config.maxIterations())
                .build();
        }

        AgentForgeConfig config() {
            return config;
        }
    }
}
