package com.agentforge.hooks;

import com.agentforge.common.error.HookException;
import com.agentforge.common.json.JsonParseException;
import com.agentforge.common.json.JsonParser;
import com.agentforge.common.json.JsonValue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Loads {@link HookDefinition} instances from a JSON configuration.
 *
 * <p>Expected format:
 * <pre>
 * [
 *   { "name": "format", "type": "post_tool_use", "command": "prettier --write", "tools": ["file_write"] },
 *   { "name": "security", "type": "pre_tool_use", "command": "check-secrets.sh", "tools": ["*"] }
 * ]
 * </pre>
 */
public final class HookConfigLoader {

    private HookConfigLoader() {}

    public static List<HookDefinition> load(String json) {
        if (json == null) {
            throw new HookException("Hook config JSON must not be null");
        }
        JsonValue parsed;
        try {
            parsed = JsonParser.parse(json);
        } catch (JsonParseException e) {
            throw new HookException("Invalid hook config JSON: " + e.getMessage(), e);
        }

        if (!(parsed instanceof JsonValue.JsonArray array)) {
            throw new HookException("Hook config must be a JSON array");
        }

        List<HookDefinition> definitions = new ArrayList<>();
        int index = 0;
        for (JsonValue element : array.elements()) {
            definitions.add(parseDefinition(element, index++));
        }
        return List.copyOf(definitions);
    }

    public static List<HookDefinition> loadFromFile(Path path) {
        String content;
        try {
            content = Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new HookException("Failed to read hook config file: " + path, e);
        }
        return load(content);
    }

    private static HookDefinition parseDefinition(JsonValue element, int index) {
        if (!(element instanceof JsonValue.JsonObject obj)) {
            throw new HookException("Hook definition at index " + index + " must be a JSON object");
        }

        String name = obj.getString("name")
                .orElseThrow(() -> new HookException(
                        "Hook definition at index " + index + " missing required field 'name'"));

        String typeStr = obj.getString("type")
                .orElseThrow(() -> new HookException(
                        "Hook definition '" + name + "' missing required field 'type'"));

        HookType type = switch (typeStr.toLowerCase()) {
            case "pre_tool_use" -> HookType.PRE_TOOL_USE;
            case "post_tool_use" -> HookType.POST_TOOL_USE;
            default -> throw new HookException(
                    "Hook definition '" + name + "' has invalid type '" + typeStr
                            + "'; expected 'pre_tool_use' or 'post_tool_use'");
        };

        String command = obj.getString("command")
                .orElseThrow(() -> new HookException(
                        "Hook definition '" + name + "' missing required field 'command'"));

        List<String> toolPatterns = obj.getArray("tools")
                .map(arr -> {
                    List<String> patterns = new ArrayList<>();
                    for (JsonValue v : arr.elements()) {
                        if (v instanceof JsonValue.JsonString s) {
                            patterns.add(s.value());
                        } else {
                            throw new HookException(
                                    "Hook definition '" + name + "' has non-string value in 'tools' array");
                        }
                    }
                    return patterns;
                })
                .orElse(List.of());

        return new HookDefinition(name, type, command, toolPatterns);
    }
}
