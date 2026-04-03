package com.agentforge.tools.builtin;

import com.agentforge.common.json.JsonParser;
import com.agentforge.common.json.JsonValue;
import com.agentforge.tools.Tool;
import com.agentforge.tools.ToolExecutor.ToolResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Built-in tool that writes content to a file, creating parent directories as needed.
 *
 * <p>Input schema: {@code {"path": "out/file.txt", "content": "..."}}
 *
 * <p>Writes are restricted to within {@code baseDirectory} to prevent path traversal.
 */
public final class FileWriteTool implements Tool {

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "Path to the file to write (relative or absolute)"
                },
                "content": {
                  "type": "string",
                  "description": "Content to write to the file"
                }
              },
              "required": ["path", "content"]
            }
            """;

    private final Path baseDirectory;

    public FileWriteTool(Path baseDirectory) {
        if (baseDirectory == null) throw new IllegalArgumentException("baseDirectory must not be null");
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "file_write";
    }

    @Override
    public String description() {
        return "Write content to a file. Creates parent directories if needed. Overwrites existing files.";
    }

    @Override
    public String inputSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(String inputJson) {
        String pathStr;
        String content;

        try {
            JsonValue parsed = JsonParser.parse(inputJson);
            if (!(parsed instanceof JsonValue.JsonObject obj)) {
                return ToolResult.error("Input must be a JSON object");
            }
            pathStr = obj.getString("path").orElse(null);
            content = obj.getString("content").orElse(null);
        } catch (Exception e) {
            return ToolResult.error("Failed to parse input: " + e.getMessage());
        }

        if (pathStr == null || pathStr.isBlank()) {
            return ToolResult.error("Missing required field: path");
        }
        if (content == null) {
            return ToolResult.error("Missing required field: content");
        }

        Path resolved = resolveSafe(pathStr);
        if (resolved == null) {
            return ToolResult.error("Path traversal detected: access denied");
        }

        try {
            Path parent = resolved.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.writeString(resolved, content);
        } catch (IOException e) {
            return ToolResult.error("Failed to write file: " + e.getMessage());
        }

        return ToolResult.success("Written " + content.length() + " characters to " + pathStr);
    }

    private Path resolveSafe(String pathStr) {
        try {
            Path candidate = baseDirectory.resolve(pathStr).normalize();
            if (!candidate.startsWith(baseDirectory)) {
                return null;
            }
            return candidate;
        } catch (Exception e) {
            return null;
        }
    }
}
