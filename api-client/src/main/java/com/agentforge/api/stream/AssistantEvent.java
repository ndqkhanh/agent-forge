package com.agentforge.api.stream;

import com.agentforge.common.model.TokenUsage;

/**
 * Sealed interface for all streaming events from an LLM.
 * All variants are immutable records. Thread-safe.
 *
 * <p>Not thread-safe for concurrent emission — designed for single-threaded stream consumption.
 */
public sealed interface AssistantEvent
    permits AssistantEvent.TextDelta,
            AssistantEvent.ToolUseStart,
            AssistantEvent.ToolUseInputDelta,
            AssistantEvent.ToolUseEnd,
            AssistantEvent.UsageUpdate,
            AssistantEvent.MessageStart,
            AssistantEvent.MessageStop,
            AssistantEvent.Error {

    /** A chunk of assistant text. */
    record TextDelta(String text) implements AssistantEvent {
        public TextDelta {
            if (text == null) throw new IllegalArgumentException("text must not be null");
        }
    }

    /** The model has started calling a tool. */
    record ToolUseStart(String id, String name) implements AssistantEvent {
        public ToolUseStart {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (name == null || name.isBlank()) throw new IllegalArgumentException("name must not be blank");
        }
    }

    /** A partial JSON fragment of the tool's input arguments. */
    record ToolUseInputDelta(String partialJson) implements AssistantEvent {
        public ToolUseInputDelta {
            if (partialJson == null) throw new IllegalArgumentException("partialJson must not be null");
        }
    }

    /** The current tool-use block has ended. */
    record ToolUseEnd() implements AssistantEvent {}

    /** Updated token usage received mid-stream. */
    record UsageUpdate(TokenUsage usage) implements AssistantEvent {
        public UsageUpdate {
            if (usage == null) throw new IllegalArgumentException("usage must not be null");
        }
    }

    /** The message stream has started; carries the message ID assigned by the provider. */
    record MessageStart(String messageId) implements AssistantEvent {
        public MessageStart {
            if (messageId == null) throw new IllegalArgumentException("messageId must not be null");
        }
    }

    /** The message stream has ended with the given stop reason (e.g. "end_turn", "tool_use"). */
    record MessageStop(String stopReason) implements AssistantEvent {
        public MessageStop {
            if (stopReason == null) throw new IllegalArgumentException("stopReason must not be null");
        }
    }

    /** An error occurred during streaming. */
    record Error(String message, String type) implements AssistantEvent {
        public Error {
            if (message == null) throw new IllegalArgumentException("message must not be null");
            if (type == null) throw new IllegalArgumentException("type must not be null");
        }
    }
}
