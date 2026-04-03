package com.agentforge.api.provider;

import com.agentforge.api.model.ModelCatalog;
import com.agentforge.api.stream.AssistantEvent;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * OpenAI-compatible API provider.
 *
 * <p>Sends POST to {@code /v1/chat/completions} with {@code stream: true}.
 * Maps the OpenAI delta SSE format to {@link AssistantEvent}.
 *
 * <p><b>Thread safety:</b> Thread-safe. Each call to {@link #streamMessage(ApiRequest)}
 * creates independent parser state.
 */
public final class OpenAiProvider implements ApiClient {

    public static final String DEFAULT_BASE_URL = "https://api.openai.com";
    public static final String PROVIDER_NAME = "openai";

    private final String apiKey;
    private final String baseUrl;
    private final HttpClient httpClient;

    public OpenAiProvider(String apiKey) {
        this(apiKey, DEFAULT_BASE_URL, defaultHttpClient());
    }

    public OpenAiProvider(String apiKey, String baseUrl, HttpClient httpClient) {
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
        return List.of(ModelCatalog.GPT_4O, ModelCatalog.GPT_4O_MINI);
    }

    @Override
    public Stream<AssistantEvent> streamMessage(ApiRequest request) {
        String body = buildRequestBody(request);

        HttpRequest httpRequest = HttpRequest.newBuilder()
            .uri(URI.create(baseUrl + "/v1/chat/completions"))
            .header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + apiKey)
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
            throw new ApiException("OpenAI API error: HTTP " + response.statusCode(),
                response.statusCode(), "http_error");
        }

        SseParser parser = new SseParser();

        return response.body()
            .flatMap(line -> parser.feed(line + "\n").stream())
            .filter(event -> event.data() != null && !"[DONE]".equals(event.data().trim()))
            .flatMap(event -> mapOpenAiEvent(event.data()).stream());
    }

    /**
     * Map an OpenAI SSE data payload to zero or more AssistantEvents.
     */
    private List<AssistantEvent> mapOpenAiEvent(String data) {
        List<AssistantEvent> events = new ArrayList<>();

        // Extract finish_reason
        String finishReason = extractJsonString(data, "finish_reason");
        if (finishReason != null && !finishReason.equals("null")) {
            events.add(new AssistantEvent.MessageStop(finishReason));
            return events;
        }

        // Extract delta content text
        String content = extractNestedJsonString(data, "delta", "content");
        if (content != null && !content.isEmpty()) {
            events.add(new AssistantEvent.TextDelta(content));
        }

        // Extract delta tool_calls (simplified: just detect tool start)
        String toolCallId = extractNestedJsonString(data, "function", "id");
        String toolCallName = extractNestedJsonString(data, "function", "name");
        if (toolCallId != null && toolCallName != null) {
            events.add(new AssistantEvent.ToolUseStart(toolCallId, toolCallName));
        }

        String toolArgs = extractNestedJsonString(data, "function", "arguments");
        if (toolArgs != null && !toolArgs.isEmpty()) {
            events.add(new AssistantEvent.ToolUseInputDelta(toolArgs));
        }

        return events;
    }

    String buildRequestBody(ApiRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"model\":").append(jsonString(request.model())).append(",");
        sb.append("\"max_tokens\":").append(request.maxTokens()).append(",");
        sb.append("\"temperature\":").append(request.temperature()).append(",");
        sb.append("\"stream\":true");

        sb.append(",\"messages\":[");
        List<ConversationMessage> messages = new ArrayList<>();

        // System prompt as a system message
        if (!request.systemPrompt().isBlank()) {
            sb.append("{\"role\":\"system\",\"content\":").append(jsonString(request.systemPrompt())).append("}");
            if (!request.messages().isEmpty()) sb.append(",");
        }

        List<ConversationMessage> reqMessages = request.messages();
        for (int i = 0; i < reqMessages.size(); i++) {
            if (i > 0) sb.append(",");
            sb.append(buildMessage(reqMessages.get(i)));
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
        String textContent = msg.textContent();
        return "{\"role\":" + jsonString(msg.role()) + ",\"content\":" + jsonString(textContent) + "}";
    }

    private String buildTool(ToolSchema tool) {
        return "{\"type\":\"function\",\"function\":{\"name\":" + jsonString(tool.name()) +
               ",\"description\":" + jsonString(tool.description()) +
               ",\"parameters\":" + tool.inputSchemaJson() + "}}";
    }

    // --- Minimal JSON extraction (same pattern as SseEventMapper) ---

    private static String extractJsonString(String json, String key) {
        if (json == null) return null;
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    private static String extractNestedJsonString(String json, String outerKey, String innerKey) {
        if (json == null) return null;
        int outerPos = json.indexOf("\"" + outerKey + "\"");
        if (outerPos < 0) return null;
        int bracePos = json.indexOf('{', outerPos + outerKey.length() + 2);
        if (bracePos < 0) return null;
        String nested = extractObject(json, bracePos);
        if (nested == null) return null;
        return extractJsonString(nested, innerKey);
    }

    private static String extractObject(String json, int bracePos) {
        int depth = 0;
        boolean inString = false;
        boolean escape = false;
        for (int i = bracePos; i < json.length(); i++) {
            char c = json.charAt(i);
            if (escape) { escape = false; continue; }
            if (c == '\\' && inString) { escape = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (!inString) {
                if (c == '{') depth++;
                else if (c == '}') {
                    depth--;
                    if (depth == 0) return json.substring(bracePos, i + 1);
                }
            }
        }
        return null;
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
