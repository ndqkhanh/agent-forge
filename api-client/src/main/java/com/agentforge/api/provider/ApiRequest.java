package com.agentforge.api.provider;

import com.agentforge.common.model.ConversationMessage;
import com.agentforge.common.model.ToolSchema;

import java.util.ArrayList;
import java.util.List;

/**
 * An immutable request to send to an LLM provider.
 * Use {@link #builder()} to construct instances.
 *
 * <p>Thread-safe. All collections are defensively copied.
 */
public record ApiRequest(
    String model,
    List<ConversationMessage> messages,
    String systemPrompt,
    List<ToolSchema> tools,
    int maxTokens,
    double temperature
) {
    /** Default values */
    public static final int DEFAULT_MAX_TOKENS = 4096;
    public static final double DEFAULT_TEMPERATURE = 1.0;

    public ApiRequest {
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model must not be blank");
        if (messages == null) throw new IllegalArgumentException("messages must not be null");
        if (maxTokens <= 0) throw new IllegalArgumentException("maxTokens must be > 0");
        if (temperature < 0 || temperature > 2) throw new IllegalArgumentException("temperature must be in [0, 2]");
        messages = List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
        systemPrompt = systemPrompt == null ? "" : systemPrompt;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String model;
        private final List<ConversationMessage> messages = new ArrayList<>();
        private String systemPrompt = "";
        private final List<ToolSchema> tools = new ArrayList<>();
        private int maxTokens = DEFAULT_MAX_TOKENS;
        private double temperature = DEFAULT_TEMPERATURE;

        private Builder() {}

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder messages(List<ConversationMessage> messages) {
            this.messages.clear();
            this.messages.addAll(messages);
            return this;
        }

        public Builder addMessage(ConversationMessage message) {
            this.messages.add(message);
            return this;
        }

        public Builder systemPrompt(String systemPrompt) {
            this.systemPrompt = systemPrompt;
            return this;
        }

        public Builder tools(List<ToolSchema> tools) {
            this.tools.clear();
            this.tools.addAll(tools);
            return this;
        }

        public Builder addTool(ToolSchema tool) {
            this.tools.add(tool);
            return this;
        }

        public Builder maxTokens(int maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder temperature(double temperature) {
            this.temperature = temperature;
            return this;
        }

        public ApiRequest build() {
            return new ApiRequest(model, messages, systemPrompt, tools, maxTokens, temperature);
        }
    }
}
