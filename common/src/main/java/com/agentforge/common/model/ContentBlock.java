package com.agentforge.common.model;

import java.util.Objects;

/**
 * Sealed hierarchy for message content blocks.
 * Text for assistant/user text, ToolUse for tool invocations, ToolResult for tool outputs.
 */
public sealed interface ContentBlock {

    record Text(String text) implements ContentBlock {
        public Text { Objects.requireNonNull(text, "text must not be null"); }
    }

    record ToolUse(String id, String name, String inputJson) implements ContentBlock {
        public ToolUse {
            Objects.requireNonNull(id, "id must not be null");
            Objects.requireNonNull(name, "name must not be null");
            inputJson = inputJson != null ? inputJson : "{}";
        }
    }

    record ToolResult(String toolUseId, String content, boolean isError) implements ContentBlock {
        public ToolResult {
            Objects.requireNonNull(toolUseId, "toolUseId must not be null");
            Objects.requireNonNull(content, "content must not be null");
        }

        public static ToolResult success(String toolUseId, String content) {
            return new ToolResult(toolUseId, content, false);
        }

        public static ToolResult error(String toolUseId, String content) {
            return new ToolResult(toolUseId, content, true);
        }
    }

    static ContentBlock text(String text) {
        return new Text(text);
    }

    static ContentBlock toolUse(String id, String name, String inputJson) {
        return new ToolUse(id, name, inputJson);
    }

    static ContentBlock toolResult(String toolUseId, String content, boolean isError) {
        return new ToolResult(toolUseId, content, isError);
    }
}
