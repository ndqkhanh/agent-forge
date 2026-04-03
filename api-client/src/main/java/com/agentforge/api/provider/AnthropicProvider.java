package com.agentforge.api.provider;

import com.agentforge.api.model.ModelCatalog;
import com.agentforge.api.stream.AssistantEvent;
import com.agentforge.api.stream.SseEventMapper;
import com.agentforge.api.stream.SseParser;
import com.agentforge.common.error.ApiException;
import com.agentforge.common.model.ContentBlock;
import com.agentforge.common.model.ConversationMessage;
import com.agentforge.common.model.ModelInfo;
import com.agentforge.common.model.ToolSchema;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

/**
 * Anthropic Claude API provider.
 *
 * <p>Uses {@link HttpClient} with streaming line-by-line SSE parsing.
 * Sends POST to {@code /v1/messages} with {@code stream: true}.
 *
 * <p><b>Thread safety:</b> Thread-safe. The underlying {@link HttpClient} is shared and
 * thread-safe. Each call to {@link #streamMessage(ApiRequest)} creates independent state.
 */
public final class AnthropicProvider implements ApiClient {

    public static final String DEFAULT_BASE_URL = "https://api.anthropic.com";
    public static final String ANTHROPIC_VERSION = "2023-06-01";
    public static final String PROVIDER_NAME = "anthropic";

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;

    public AnthropicProvider(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL, defaultHttpClient());
    }

    public AnthropicProvider(String apiKey, String baseUrl, HttpClient httpClient) {
        if (apiKey == null || apiKey.isBlank()) throw new IllegalArgumentException("apiKey must not be blank");
        if (baseUrl == null || baseUrl.isBlank()) throw new IllegalArgumentException("baseUrl must not be blank");
        if (httpClient == null) throw new IllegalArgumentException("httpClient must not be null");
        this.apiKey = apiKey;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.httpClient = httpClient;
    }

    private static HttpClient defaultHttpClient() {
        return HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(30))
            .build();
    }

    @Override
    public String providerName() {
        return PROVIDER_NAME;
    }

    @Override
    public List<ModelInfo> availableModels() {
        return List.of(ModelCatalog.CLAUDE_OPUS, ModelCatalog.CLAUDE_SONNET, ModelCatalog.CLAUDE_HAIKU);
    }

    @Override
    public Stream<AssistantEvent> streamMessage(ApiRequest request) {
        String body = buildRequestBody(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/v1/messages"))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .header("Accept", "text/event-stream")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<Stream<String>> response;
        try {
            response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());
        } catch (IOException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ApiException("HTTP request failed: " + e.getMessage(), e);
        }

        if (response.statusCode() >= 400) {
            throw new ApiException("Anthropic API error: HTTP " + response.statusCode(),
                response.statusCode(), "http_error");
        }

        SseParser parser = new SseParser();
        SseEventMapper mapper = new SseEventMapper();

        return response.body()
            .flatMap(line -> parser.feed(line + "\n").stream())
            .map(mapper::map)
            .filter(opt -> opt.isPresent())
            .map(opt -> opt.get());
    }

    /**
     * Build the Anthropic Messages API JSON request body.
     * Uses manual JSON construction to avoid external dependencies.
     */
    String buildRequestBody(ApiRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"model\":").append(jsonString(request.model())).append(",");
        sb.append("\"max_tokens\":").append(request.maxTokens()).append(",");
        sb.append("\"temperature\":").append(request.temperature()).append(",");
        sb.append("\"stream\":true");

        if (!request.systemPrompt().isBlank()) {
            sb.append(",\"system\":").append(jsonString(request.systemPrompt()));
        }

        sb.append(",\"messages\":[");
        List<ConversationMessage> messages = request.messages();
        for (int i = 0; i < messages.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(buildMessage(messages.get(i)));
        }
        sb.append("]");

        if (!request.tools().isEmpty()) {
            sb.append(",\"tools\":[");
            List<ToolSchema> tools = request.tools();
            for (int i = 0; i < tools.size(); i++) {
                if (i > 0) sb.append(",");
                sb.append(buildTool(tools.get(i)));
            }
            sb.append("]");
        }

        sb.append("}");
        return sb.toString();
    }

    private String buildMessage(ConversationMessage msg) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"role\":").append(jsonString(msg.role()));
        sb.append(",\"content\":[");
        List<ContentBlock> blocks = msg.blocks();
        for (int i = 0; i < blocks.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(buildContentBlock(blocks.get(i)));
        }
        sb.append("]}");
        return sb.toString();
    }

    private String buildContentBlock(ContentBlock block) {
        return switch (block) {
            case ContentBlock.Text t ->
                "{\"type\":\"text\",\"text\":" + jsonString(t.text()) + "}";
            case ContentBlock.ToolUse tu ->
                "{\"type\":\"tool_use\",\"id\":" + jsonString(tu.id()) +
                ",\"name\":" + jsonString(tu.name()) +
                ",\"input\":" + tu.inputJson() + "}";
            case ContentBlock.ToolResult tr ->
                "{\"type\":\"tool_result\",\"tool_use_id\":" + jsonString(tr.toolUseId()) +
                ",\"content\":" + jsonString(tr.content()) +
                ",\"is_error\":" + tr.isError() + "}";
        };
    }

    private String buildTool(ToolSchema tool) {
        return "{\"name\":" + jsonString(tool.name()) +
               ",\"description\":" + jsonString(tool.description()) +
               ",\"input_schema\":" + tool.inputSchemaJson() + "}";
    }

    static String jsonString(String s) {
        if (s == null) return "null";
        return "\"" + s.replace("\\", "\\\\")
                       .replace("\"", "\\\"")
                       .replace("\n", "\\n")
                       .replace("\r", "\\r")
                       .replace("\t", "\\t") + "\"";
    }
}
