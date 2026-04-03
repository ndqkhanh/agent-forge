package com.agentforge.tools.registry;

import com.agentforge.common.model.ToolSchema;
import com.agentforge.tools.Tool;
import com.agentforge.tools.ToolExecutor;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Unified, immutable registry of tools. Implements ToolExecutor so it can be passed
 * directly to any agent runtime.
 *
 * <p>Build with {@link Builder}:
 * <pre>{@code
 * ToolRegistry registry = ToolRegistry.builder()
 *     .register(new BashTool(workDir, 120_000))
 *     .registerAll(List.of(new FileReadTool(base), new FileWriteTool(base)))
 *     .build();
 * }</pre>
 */
public final class ToolRegistry implements ToolExecutor {

    private final Map<String, Tool> tools;

    private ToolRegistry(Map<String, Tool> tools) {
        this.tools = Map.copyOf(tools);
    }

    // -------------------------------------------------------------------------
    // ToolExecutor
    // -------------------------------------------------------------------------

    @Override
    public ToolResult execute(String toolName, String inputJson) {
        Tool tool = tools.get(toolName);
        if (tool == null) {
            return ToolResult.error("Unknown tool: " + toolName);
        }
        try {
            return tool.execute(inputJson);
        } catch (Exception e) {
            return ToolResult.error("Tool execution failed: " + e.getMessage());
        }
    }

    @Override
    public boolean supports(String toolName) {
        return tools.containsKey(toolName);
    }

    // -------------------------------------------------------------------------
    // Registry inspection
    // -------------------------------------------------------------------------

    /**
     * Returns schemas for all registered tools, in registration order.
     *
     * @return unmodifiable list of ToolSchema records
     */
    public List<ToolSchema> allSchemas() {
        return tools.values().stream()
                .map(Tool::toSchema)
                .toList();
    }

    /** Number of registered tools. */
    public int size() {
        return tools.size();
    }

    /** Names of all registered tools (unmodifiable, insertion-ordered). */
    public Set<String> toolNames() {
        return tools.keySet();
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {

        private final Map<String, Tool> tools = new LinkedHashMap<>();

        /** Register a single tool (overwrites any existing tool with the same name). */
        public Builder register(Tool tool) {
            tools.put(tool.name(), tool);
            return this;
        }

        /** Register all tools in the collection. */
        public Builder registerAll(Collection<? extends Tool> toolCollection) {
            toolCollection.forEach(this::register);
            return this;
        }

        /** Merge all tools from another registry into this builder. */
        public Builder merge(ToolRegistry other) {
            tools.putAll(other.tools);
            return this;
        }

        /** Build the immutable registry. */
        public ToolRegistry build() {
            return new ToolRegistry(tools);
        }
    }
}
