package com.agentforge.api.stream;

import com.agentforge.common.model.TokenUsage;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Maps raw {@link SseParser.SseEvent} objects to typed {@link AssistantEvent} instances.
 *
 * <p>Handles Anthropic Messages API streaming event types. Unknown event types return
 * {@link Optional#empty()}.
 *
 * <p><b>Thread safety:</b> Instances are stateful (tracks current tool-use block index).
 * NOT thread-safe. Use one instance per stream.
 */
public final class SseEventMapper {

    // Tracks the current content block index to distinguish text vs tool_use blocks
    private int currentBlockIndex = -1;
    private String currentBlockType = null; // "text" or "tool_use"

    /**
     * Map a raw SSE event to an {@link AssistantEvent}.
     *
     * @param event the raw SSE event
     * @return the mapped event, or empty if the event type is unknown or not actionable
     */
    public Optional<AssistantEvent> map(SseParser.SseEvent event) {
        if (event.event() == null) {
            return Optional.empty();
        }

        return switch (event.event()) {
            case "message_start" -> mapMessageStart(event.data());
            case "content_block_start" -> mapContentBlockStart(event.data());
            case "content_block_delta" -> mapContentBlockDelta(event.data());
            case "content_block_stop" -> mapContentBlockStop();
            case "message_delta" -> mapMessageDelta(event.data());
            case "message_stop" -> Optional.of(new AssistantEvent.MessageStop("end_turn"));
            case "error" -> mapError(event.data());
            default -> Optional.empty();
        };
    }

    private Optional<AssistantEvent> mapMessageStart(String data) {
        String id = extractJsonString(data, "id");
        if (id == null) id = "";
        return Optional.of(new AssistantEvent.MessageStart(id));
    }

    private Optional<AssistantEvent> mapContentBlockStart(String data) {
        // Extract index
        String indexStr = extractJsonValue(data, "index");
        if (indexStr != null) {
            try {
                currentBlockIndex = Integer.parseInt(indexStr.trim());
            } catch (NumberFormatException ignored) {}
        }

        // Extract type from content_block object
        String type = extractNestedJsonString(data, "content_block", "type");
        currentBlockType = type;

        if ("tool_use".equals(type)) {
            String id = extractNestedJsonString(data, "content_block", "id");
            String name = extractNestedJsonString(data, "content_block", "name");
            if (id == null) id = "";
            if (name == null) name = "";
            return Optional.of(new AssistantEvent.ToolUseStart(id.isBlank() ? "unknown" : id,
                                                                name.isBlank() ? "unknown" : name));
        }
        // text blocks don't emit a start event to callers
        return Optional.empty();
    }

    private Optional<AssistantEvent> mapContentBlockDelta(String data) {
        String deltaType = extractNestedJsonString(data, "delta", "type");

        if ("text_delta".equals(deltaType)) {
            String text = extractNestedJsonString(data, "delta", "text");
            if (text == null) text = "";
            return Optional.of(new AssistantEvent.TextDelta(text));
        }

        if ("input_json_delta".equals(deltaType)) {
            String partial = extractNestedJsonString(data, "delta", "partial_json");
            if (partial == null) partial = "";
            return Optional.of(new AssistantEvent.ToolUseInputDelta(partial));
        }

        return Optional.empty();
    }

    private Optional<AssistantEvent> mapContentBlockStop() {
        if ("tool_use".equals(currentBlockType)) {
            currentBlockType = null;
            return Optional.of(new AssistantEvent.ToolUseEnd());
        }
        currentBlockType = null;
        return Optional.empty();
    }

    private Optional<AssistantEvent> mapMessageDelta(String data) {
        // Extract stop_reason and usage
        String stopReason = extractNestedJsonString(data, "delta", "stop_reason");

        // Try to extract usage
        String inputTokensStr = extractNestedJsonValue(data, "usage", "input_tokens");
        String outputTokensStr = extractNestedJsonValue(data, "usage", "output_tokens");
        String cacheReadStr = extractNestedJsonValue(data, "usage", "cache_read_input_tokens");
        String cacheWriteStr = extractNestedJsonValue(data, "usage", "cache_creation_input_tokens");

        int inputTokens = parseIntOrZero(inputTokensStr);
        int outputTokens = parseIntOrZero(outputTokensStr);
        int cacheRead = parseIntOrZero(cacheReadStr);
        int cacheWrite = parseIntOrZero(cacheWriteStr);

        if (inputTokens > 0 || outputTokens > 0) {
            return Optional.of(new AssistantEvent.UsageUpdate(
                new TokenUsage(inputTokens, outputTokens, cacheRead, cacheWrite)));
        }

        if (stopReason != null) {
            return Optional.of(new AssistantEvent.MessageStop(stopReason));
        }

        return Optional.empty();
    }

    private Optional<AssistantEvent> mapError(String data) {
        String message = extractNestedJsonString(data, "error", "message");
        String type = extractNestedJsonString(data, "error", "type");
        if (message == null) message = data != null ? data : "unknown error";
        if (type == null) type = "api_error";
        return Optional.of(new AssistantEvent.Error(message, type));
    }

    // --- Minimal JSON extraction helpers (no external dependencies) ---

    /** Extract top-level string value for a key: {"key":"value"} */
    private static String extractJsonString(String json, String key) {
        if (json == null) return null;
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:[^\"\\\\]|\\\\.)*)\"");
        Matcher m = p.matcher(json);
        return m.find() ? unescape(m.group(1)) : null;
    }

    /** Extract top-level numeric/boolean/null value for a key (unquoted) */
    private static String extractJsonValue(String json, String key) {
        if (json == null) return null;
        Pattern p = Pattern.compile("\"" + Pattern.quote(key) + "\"\\s*:\\s*([0-9]+(?:\\.[0-9]+)?|true|false|null)");
        Matcher m = p.matcher(json);
        return m.find() ? m.group(1) : null;
    }

    /**
     * Extract a string value from a nested object. Finds the first occurrence of
     * {"outerKey":{...,"innerKey":"value",...}} using a simple heuristic.
     */
    private static String extractNestedJsonString(String json, String outerKey, String innerKey) {
        if (json == null) return null;
        // Find the outer key and then extract inner key from its object value
        int outerPos = json.indexOf("\"" + outerKey + "\"");
        if (outerPos < 0) return null;
        // Find the opening brace of the value
        int bracePos = json.indexOf('{', outerPos + outerKey.length() + 2);
        if (bracePos < 0) return null;
        // Extract the nested object (find balanced closing brace)
        String nested = extractObject(json, bracePos);
        if (nested == null) return null;
        return extractJsonString(nested, innerKey);
    }

    private static String extractNestedJsonValue(String json, String outerKey, String innerKey) {
        if (json == null) return null;
        int outerPos = json.indexOf("\"" + outerKey + "\"");
        if (outerPos < 0) return null;
        int bracePos = json.indexOf('{', outerPos + outerKey.length() + 2);
        if (bracePos < 0) return null;
        String nested = extractObject(json, bracePos);
        if (nested == null) return null;
        return extractJsonValue(nested, innerKey);
    }

    /** Extract the JSON object starting at bracePos (balanced braces). */
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

    private static String unescape(String s) {
        return s.replace("\\\"", "\"")
                .replace("\\\\", "\\")
                .replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t");
    }

    private static int parseIntOrZero(String s) {
        if (s == null) return 0;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    /** Reset mapper state for reuse across multiple streams. */
    public void reset() {
        currentBlockIndex = -1;
        currentBlockType = null;
    }
}
