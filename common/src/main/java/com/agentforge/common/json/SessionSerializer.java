package com.agentforge.common.json;

import com.agentforge.common.model.ContentBlock;
import com.agentforge.common.model.ConversationMessage;
import com.agentforge.common.model.Session;
import com.agentforge.common.model.TokenUsage;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Converts Session ↔ JSON string with full round-trip fidelity.
 */
public final class SessionSerializer {

    private SessionSerializer() {}

    public static String serialize(Session session) {
        JsonValue.JsonObject obj = buildSessionObject(session);
        return JsonWriter.write(obj);
    }

    public static Session deserialize(String json) {
        JsonValue parsed = JsonParser.parse(json);
        if (!(parsed instanceof JsonValue.JsonObject root)) {
            throw new JsonParseException("Expected JSON object at root");
        }
        int version = root.getNumber("version")
                .map(Double::intValue)
                .orElseThrow(() -> new JsonParseException("Missing 'version' field"));
        String id = root.getString("id")
                .orElseThrow(() -> new JsonParseException("Missing 'id' field"));
        TokenUsage totalUsage = root.getObject("totalUsage")
                .map(SessionSerializer::parseTokenUsage)
                .orElse(TokenUsage.ZERO);
        List<ConversationMessage> messages = root.getArray("messages")
                .map(SessionSerializer::parseMessages)
                .orElse(List.of());
        return new Session(version, id, messages, totalUsage);
    }

    // --- serialize helpers ---

    private static JsonValue.JsonObject buildSessionObject(Session session) {
        Map<String, JsonValue> fields = new LinkedHashMap<>();
        fields.put("version", new JsonValue.JsonNumber(session.version()));
        fields.put("id", new JsonValue.JsonString(session.id()));
        fields.put("totalUsage", buildTokenUsageObject(session.totalUsage()));
        List<JsonValue> msgList = new ArrayList<>();
        for (ConversationMessage msg : session.messages()) {
            msgList.add(buildMessageObject(msg));
        }
        fields.put("messages", new JsonValue.JsonArray(msgList));
        return new JsonValue.JsonObject(fields);
    }

    private static JsonValue.JsonObject buildTokenUsageObject(TokenUsage usage) {
        Map<String, JsonValue> fields = new LinkedHashMap<>();
        fields.put("inputTokens", new JsonValue.JsonNumber(usage.inputTokens()));
        fields.put("outputTokens", new JsonValue.JsonNumber(usage.outputTokens()));
        fields.put("cacheReadTokens", new JsonValue.JsonNumber(usage.cacheReadTokens()));
        fields.put("cacheWriteTokens", new JsonValue.JsonNumber(usage.cacheWriteTokens()));
        return new JsonValue.JsonObject(fields);
    }

    private static JsonValue.JsonObject buildMessageObject(ConversationMessage msg) {
        Map<String, JsonValue> fields = new LinkedHashMap<>();
        fields.put("role", new JsonValue.JsonString(msg.role()));
        fields.put("usage", buildTokenUsageObject(msg.usage()));
        List<JsonValue> blockList = new ArrayList<>();
        for (ContentBlock block : msg.blocks()) {
            blockList.add(buildBlockObject(block));
        }
        fields.put("blocks", new JsonValue.JsonArray(blockList));
        return new JsonValue.JsonObject(fields);
    }

    private static JsonValue.JsonObject buildBlockObject(ContentBlock block) {
        Map<String, JsonValue> fields = new LinkedHashMap<>();
        switch (block) {
            case ContentBlock.Text t -> {
                fields.put("type", new JsonValue.JsonString("text"));
                fields.put("text", new JsonValue.JsonString(t.text()));
            }
            case ContentBlock.ToolUse u -> {
                fields.put("type", new JsonValue.JsonString("tool_use"));
                fields.put("id", new JsonValue.JsonString(u.id()));
                fields.put("name", new JsonValue.JsonString(u.name()));
                fields.put("inputJson", new JsonValue.JsonString(u.inputJson()));
            }
            case ContentBlock.ToolResult r -> {
                fields.put("type", new JsonValue.JsonString("tool_result"));
                fields.put("toolUseId", new JsonValue.JsonString(r.toolUseId()));
                fields.put("content", new JsonValue.JsonString(r.content()));
                fields.put("isError", new JsonValue.JsonBool(r.isError()));
            }
        }
        return new JsonValue.JsonObject(fields);
    }

    // --- deserialize helpers ---

    private static TokenUsage parseTokenUsage(JsonValue.JsonObject obj) {
        int input = obj.getNumber("inputTokens").map(Double::intValue).orElse(0);
        int output = obj.getNumber("outputTokens").map(Double::intValue).orElse(0);
        int cacheRead = obj.getNumber("cacheReadTokens").map(Double::intValue).orElse(0);
        int cacheWrite = obj.getNumber("cacheWriteTokens").map(Double::intValue).orElse(0);
        return new TokenUsage(input, output, cacheRead, cacheWrite);
    }

    private static List<ConversationMessage> parseMessages(JsonValue.JsonArray arr) {
        List<ConversationMessage> result = new ArrayList<>();
        for (JsonValue elem : arr.elements()) {
            if (!(elem instanceof JsonValue.JsonObject msgObj)) {
                throw new JsonParseException("Expected message to be a JSON object");
            }
            result.add(parseMessage(msgObj));
        }
        return result;
    }

    private static ConversationMessage parseMessage(JsonValue.JsonObject obj) {
        String role = obj.getString("role")
                .orElseThrow(() -> new JsonParseException("Missing 'role' in message"));
        TokenUsage usage = obj.getObject("usage")
                .map(SessionSerializer::parseTokenUsage)
                .orElse(TokenUsage.ZERO);
        List<ContentBlock> blocks = obj.getArray("blocks")
                .map(SessionSerializer::parseBlocks)
                .orElse(List.of());
        return new ConversationMessage(role, blocks, usage);
    }

    private static List<ContentBlock> parseBlocks(JsonValue.JsonArray arr) {
        List<ContentBlock> result = new ArrayList<>();
        for (JsonValue elem : arr.elements()) {
            if (!(elem instanceof JsonValue.JsonObject blockObj)) {
                throw new JsonParseException("Expected block to be a JSON object");
            }
            result.add(parseBlock(blockObj));
        }
        return result;
    }

    private static ContentBlock parseBlock(JsonValue.JsonObject obj) {
        String type = obj.getString("type")
                .orElseThrow(() -> new JsonParseException("Missing 'type' in block"));
        return switch (type) {
            case "text" -> {
                String text = obj.getString("text")
                        .orElseThrow(() -> new JsonParseException("Missing 'text' in text block"));
                yield new ContentBlock.Text(text);
            }
            case "tool_use" -> {
                String id = obj.getString("id")
                        .orElseThrow(() -> new JsonParseException("Missing 'id' in tool_use block"));
                String name = obj.getString("name")
                        .orElseThrow(() -> new JsonParseException("Missing 'name' in tool_use block"));
                String inputJson = obj.getString("inputJson")
                        .orElseThrow(() -> new JsonParseException("Missing 'inputJson' in tool_use block"));
                yield new ContentBlock.ToolUse(id, name, inputJson);
            }
            case "tool_result" -> {
                String toolUseId = obj.getString("toolUseId")
                        .orElseThrow(() -> new JsonParseException("Missing 'toolUseId' in tool_result block"));
                String content = obj.getString("content")
                        .orElseThrow(() -> new JsonParseException("Missing 'content' in tool_result block"));
                boolean isError = obj.getBool("isError").orElse(false);
                yield new ContentBlock.ToolResult(toolUseId, content, isError);
            }
            default -> throw new JsonParseException("Unknown block type: " + type);
        };
    }
}
