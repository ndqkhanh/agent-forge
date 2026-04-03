package com.agentforge.api.provider;

import com.agentforge.common.model.ConversationMessage;
import com.agentforge.common.model.ToolSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ApiRequest")
class ApiRequestTest {

    @Test
    @DisplayName("builder creates valid request with all fields")
    void builderCreatesValidRequest() {
        ConversationMessage msg = ConversationMessage.userText("hi");
        ToolSchema tool = ToolSchema.noParams("search", "Search the web");

        ApiRequest request = ApiRequest.builder()
            .model("claude-sonnet-4-6")
            .addMessage(msg)
            .systemPrompt("You are a helpful assistant.")
            .addTool(tool)
            .maxTokens(2048)
            .temperature(0.7)
            .build();

        assertThat(request.model()).isEqualTo("claude-sonnet-4-6");
        assertThat(request.messages()).containsExactly(msg);
        assertThat(request.systemPrompt()).isEqualTo("You are a helpful assistant.");
        assertThat(request.tools()).containsExactly(tool);
        assertThat(request.maxTokens()).isEqualTo(2048);
        assertThat(request.temperature()).isEqualTo(0.7);
    }

    @Test
    @DisplayName("default values applied when not set")
    void defaultValues() {
        ApiRequest request = ApiRequest.builder()
            .model("gpt-4o")
            .build();

        assertThat(request.maxTokens()).isEqualTo(ApiRequest.DEFAULT_MAX_TOKENS);
        assertThat(request.temperature()).isEqualTo(ApiRequest.DEFAULT_TEMPERATURE);
        assertThat(request.systemPrompt()).isEmpty();
        assertThat(request.tools()).isEmpty();
        assertThat(request.messages()).isEmpty();
    }

    @Test
    @DisplayName("messages list is immutable — external modification has no effect")
    void messagesListIsImmutable() {
        ApiRequest request = ApiRequest.builder()
            .model("gpt-4o")
            .addMessage(ConversationMessage.userText("hello"))
            .build();

        assertThatThrownBy(() -> request.messages().add(ConversationMessage.userText("extra")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("tools list is immutable")
    void toolsListIsImmutable() {
        ApiRequest request = ApiRequest.builder().model("gpt-4o").build();
        assertThatThrownBy(() -> request.tools().add(ToolSchema.noParams("t", "d")))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("invalid model throws IllegalArgumentException")
    void blankModelThrows() {
        assertThatThrownBy(() -> ApiRequest.builder().model("").build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("null model throws IllegalArgumentException")
    void nullModelThrows() {
        assertThatThrownBy(() -> ApiRequest.builder().model(null).build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("maxTokens <= 0 throws IllegalArgumentException")
    void invalidMaxTokensThrows() {
        assertThatThrownBy(() -> ApiRequest.builder().model("gpt-4o").maxTokens(0).build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("temperature out of range throws IllegalArgumentException")
    void invalidTemperatureThrows() {
        assertThatThrownBy(() -> ApiRequest.builder().model("gpt-4o").temperature(3.0).build())
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("messages method on builder replaces previous messages")
    void builderMessagesReplacesPrevious() {
        ConversationMessage m1 = ConversationMessage.userText("first");
        ConversationMessage m2 = ConversationMessage.userText("second");

        ApiRequest request = ApiRequest.builder()
            .model("gpt-4o")
            .addMessage(m1)
            .messages(List.of(m2))
            .build();

        assertThat(request.messages()).containsExactly(m2);
    }
}
