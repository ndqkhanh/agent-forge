package com.agentforge.config.loader;

import com.agentforge.common.error.ConfigException;
import com.agentforge.common.json.JsonParseException;
import com.agentforge.common.json.JsonParser;
import com.agentforge.common.json.JsonValue;
import com.agentforge.config.permission.PermissionLevel;
import com.agentforge.config.permission.ToolPermission;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Loads and merges AgentForge configuration from three sources.
 * Priority (highest to lowest): local > project > user > defaults
 */
public final class ConfigLoader {

    private final Path userConfigPath;
    private final Path projectConfigPath;
    private final Path localConfigPath;

    private ConfigLoader(Builder builder) {
        this.userConfigPath = builder.userConfigPath;
        this.projectConfigPath = builder.projectConfigPath;
        this.localConfigPath = builder.localConfigPath;
    }

    /**
     * Load and merge configurations.
     * Priority: local > project > user > defaults
     */
    public AgentForgeConfig load() {
        AgentForgeConfig config = AgentForgeConfig.defaults();
        config = merge(config, loadFromFile(userConfigPath));
        config = merge(config, loadFromFile(projectConfigPath));
        config = merge(config, loadFromFile(localConfigPath));
        return config;
    }

    /**
     * Parse a JSON config file into field overrides.
     * Returns empty map if file doesn't exist.
     * Throws ConfigException if file exists but is invalid JSON.
     */
    private Map<String, JsonValue> loadFromFile(Path path) {
        if (path == null || !Files.exists(path)) {
            return Map.of();
        }
        try {
            String content = Files.readString(path, StandardCharsets.UTF_8);
            JsonValue parsed = JsonParser.parse(content);
            if (!(parsed instanceof JsonValue.JsonObject obj)) {
                throw new ConfigException("Config file must be a JSON object: " + path);
            }
            return obj.fields();
        } catch (JsonParseException e) {
            throw new ConfigException("Invalid JSON in config file: " + path, e);
        } catch (IOException e) {
            throw new ConfigException("Failed to read config file: " + path, e);
        }
    }

    /**
     * Merge overrides into existing config.
     * Only fields present in overrides replace existing values.
     */
    private AgentForgeConfig merge(AgentForgeConfig base, Map<String, JsonValue> overrides) {
        if (overrides.isEmpty()) {
            return base;
        }

        String model = base.model();
        int maxTokens = base.maxTokens();
        double temperature = base.temperature();
        int maxIterations = base.maxIterations();
        PermissionLevel permissionLevel = base.permissionLevel();
        List<ToolPermission> toolPermissions = new ArrayList<>(base.toolPermissions());
        int compactionThreshold = base.compactionThreshold();
        int contextWindow = base.contextWindow();
        Map<String, String> providerApiKeys = new HashMap<>(base.providerApiKeys());
        List<String> instructionFiles = new ArrayList<>(base.instructionFiles());
        Map<String, Object> mcpServers = new HashMap<>(base.mcpServers());
        Map<String, String> extra = new LinkedHashMap<>(base.extra());

        for (Map.Entry<String, JsonValue> entry : overrides.entrySet()) {
            String key = entry.getKey();
            JsonValue value = entry.getValue();

            switch (key) {
                case "model" -> {
                    if (value instanceof JsonValue.JsonString s) model = s.value();
                }
                case "maxTokens" -> {
                    if (value instanceof JsonValue.JsonNumber n) maxTokens = (int) n.value();
                }
                case "temperature" -> {
                    if (value instanceof JsonValue.JsonNumber n) temperature = n.value();
                }
                case "maxIterations" -> {
                    if (value instanceof JsonValue.JsonNumber n) maxIterations = (int) n.value();
                }
                case "permissionLevel" -> {
                    if (value instanceof JsonValue.JsonString s) {
                        try {
                            permissionLevel = PermissionLevel.valueOf(s.value());
                        } catch (IllegalArgumentException ignored) {
                            // keep existing if unrecognized
                        }
                    }
                }
                case "compactionThreshold" -> {
                    if (value instanceof JsonValue.JsonNumber n) compactionThreshold = (int) n.value();
                }
                case "contextWindow" -> {
                    if (value instanceof JsonValue.JsonNumber n) contextWindow = (int) n.value();
                }
                case "providerApiKeys" -> {
                    if (value instanceof JsonValue.JsonObject obj) {
                        Map<String, String> merged = new HashMap<>(providerApiKeys);
                        for (Map.Entry<String, JsonValue> kv : obj.fields().entrySet()) {
                            if (kv.getValue() instanceof JsonValue.JsonString sv) {
                                merged.put(kv.getKey(), sv.value());
                            }
                        }
                        providerApiKeys = merged;
                    }
                }
                case "instructionFiles" -> {
                    if (value instanceof JsonValue.JsonArray arr) {
                        List<String> files = new ArrayList<>();
                        for (JsonValue el : arr.elements()) {
                            if (el instanceof JsonValue.JsonString s) files.add(s.value());
                        }
                        instructionFiles = files;
                    }
                }
                case "mcpServers" -> {
                    if (value instanceof JsonValue.JsonObject obj) {
                        Map<String, Object> merged = new HashMap<>(mcpServers);
                        for (Map.Entry<String, JsonValue> kv : obj.fields().entrySet()) {
                            merged.put(kv.getKey(), jsonValueToObject(kv.getValue()));
                        }
                        mcpServers = merged;
                    }
                }
                default -> {
                    // treat unknown keys as extra string values
                    if (value instanceof JsonValue.JsonString s) {
                        extra.put(key, s.value());
                    }
                }
            }
        }

        return new AgentForgeConfig(
            model, maxTokens, temperature, maxIterations,
            permissionLevel, toolPermissions,
            compactionThreshold, contextWindow,
            providerApiKeys, instructionFiles, mcpServers, extra
        );
    }

    private Object jsonValueToObject(JsonValue value) {
        return switch (value) {
            case JsonValue.JsonString s -> s.value();
            case JsonValue.JsonNumber n -> n.value();
            case JsonValue.JsonBool b -> b.value();
            case JsonValue.JsonNull ignored -> null;
            case JsonValue.JsonArray arr -> {
                List<Object> list = new ArrayList<>();
                for (JsonValue el : arr.elements()) list.add(jsonValueToObject(el));
                yield list;
            }
            case JsonValue.JsonObject obj -> {
                Map<String, Object> map = new LinkedHashMap<>();
                for (Map.Entry<String, JsonValue> e : obj.fields().entrySet()) {
                    map.put(e.getKey(), jsonValueToObject(e.getValue()));
                }
                yield map;
            }
        };
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private Path userConfigPath;
        private Path projectConfigPath;
        private Path localConfigPath;

        public Builder userConfig(Path path) {
            this.userConfigPath = path;
            return this;
        }

        public Builder projectConfig(Path path) {
            this.projectConfigPath = path;
            return this;
        }

        public Builder localConfig(Path path) {
            this.localConfigPath = path;
            return this;
        }

        public ConfigLoader build() {
            return new ConfigLoader(this);
        }
    }
}
