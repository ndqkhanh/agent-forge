package com.agentforge.api.provider;

import com.agentforge.api.model.ModelCatalog;
import com.agentforge.common.model.ConversationMessage;
import com.agentforge.common.model.ModelInfo;
import com.agentforge.common.model.ToolSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("AnthropicProvider (unit — no HTTP)")
class AnthropicProviderTest {

    private final AnthropicProvider provider =
        new AnthropicProvider("test-key", "https://api.anthropic.com", java.net.http.HttpClient.newHttpClient());

    @Test
    @DisplayName("providerName returns 'anthropic'")
    void providerName() {
        assertThat(provider.providerName()).isEqualTo("anthropic");
    }

    @Test
    @DisplayName("availableModels includes Opus, Sonnet, Haiku")
    void availableModels() {
        List<ModelInfo> models = provider.availableModels();
        assertThat(models).contains(ModelCatalog.CLAUDE_OPUS, ModelCatalog.CLAUDE_SONNET, ModelCatalog.CLAUDE_HAIKU);
    }

    @Test
    @DisplayName("buildRequestBody includes model and stream:true")
    void buildRequestBodyContainsModelAndStream() {
        ApiRequest request = ApiRequest.builder()
            .model("claude-sonnet-4-6")
            .addMessage(ConversationMessage.userText("hello"))
            .build();

        String body = provider.buildRequestBody(request);
        assertThat(body).contains("\"model\":\"claude-sonnet-4-6\"");
        assertThat(body).contains("\"stream\":true");
    }

    @Test
    @DisplayName("buildRequestBody includes system prompt when non-blank")
    void buildRequestBodyIncludesSystemPrompt() {
        ApiRequest request = ApiRequest.builder()
            .model("claude-opus-4-6")
            .systemPrompt("You are a helpful assistant.")
            .build();

        String body = provider.buildRequestBody(request);
        assertThat(body).contains("\"system\":\"You are a helpful assistant.\"");
    }

    @Test
    @DisplayName("buildRequestBody omits system field when systemPrompt is blank")
    void buildRequestBodyOmitsBlankSystemPrompt() {
        ApiRequest request = ApiRequest.builder()
            .model("claude-opus-4-6")
            .build();

        String body = provider.buildRequestBody(request);
        assertThat(body).doesNotContain("\"system\"");
    }

    @Test
    @DisplayName("buildRequestBody includes tools when provided")
    void buildRequestBodyIncludesTools() {
        ApiRequest request = ApiRequest.builder()
            .model("claude-sonnet-4-6")
            .addTool(new ToolSchema("search", "Search the web", "{\"type\":\"object\"}"))
            .build();

        String body = provider.buildRequestBody(request);
        assertThat(body).contains("\"tools\"");
        assertThat(body).contains("\"name\":\"search\"");
    }

    @Test
    @DisplayName("buildRequestBody includes max_tokens")
    void buildRequestBodyIncludesMaxTokens() {
        ApiRequest request = ApiRequest.builder()
            .model("claude-opus-4-6")
            .maxTokens(1024)
            .build();

        String body = provider.buildRequestBody(request);
        assertThat(body).contains("\"max_tokens\":1024");
    }

    @Test
    @DisplayName("jsonString escapes double quotes and backslashes")
    void jsonStringEscapesSpecialChars() {
        assertThat(AnthropicProvider.jsonString("say \"hello\""))
            .isEqualTo("\"say \\\"hello\\\"\"");
        assertThat(AnthropicProvider.jsonString("path\\to\\file"))
            .isEqualTo("\"path\\\\to\\\\file\"");
    }

    @Test
    @DisplayName("blank apiKey throws IllegalArgumentException")
    void blankApiKeyThrows() {
        assertThatThrownBy(() -> new AnthropicProvider("", "https://api.anthropic.com",
                java.net.http.HttpClient.newHttpClient()))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("availableModels returns immutable list")
    void availableModelsIsImmutable() {
        assertThatThrownBy(() -> provider.availableModels().add(ModelCatalog.GPT_4O))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
