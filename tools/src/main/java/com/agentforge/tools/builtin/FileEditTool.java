package com.agentforge.tools.builtin;

import com.agentforge.common.json.JsonParser;
import com.agentforge.common.json.JsonValue;
import com.agentforge.tools.Tool;
import com.agentforge.tools.ToolExecutor.ToolResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Built-in tool that edits a file by replacing a unique string occurrence.
 *
 * <p>Input schema:
 * <pre>{@code {"path": "src/Main.java", "old_string": "foo", "new_string": "bar"}}</pre>
 *
 * <p>The {@code old_string} must appear exactly once in the file; if it appears
 * zero or multiple times the operation is rejected with an error.
 *
 * <p>Writes are restricted to within {@code baseDirectory} to prevent path traversal.
 */
public final class FileEditTool implements Tool {

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "Path to the file to edit"
                },
                "old_string": {
                  "type": "string",
                  "description": "The exact string to find and replace (must appear exactly once)"
                },
                "new_string": {
                  "type": "string",
                  "description": "The replacement string"
                }
              },
              "required": ["path", "old_string", "new_string"]
            }
            """;

    private final Path baseDirectory;

    public FileEditTool(Path baseDirectory) {
        if (baseDirectory == null) throw new IllegalArgumentException("baseDirectory must not be null");
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "file_edit";
    }

    @Override
    public String description() {
        return "Edit a file by replacing a unique string occurrence. The old_string must appear exactly once.";
    }

    @Override
    public String inputSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(String inputJson) {
        String pathStr;
        String oldString;
        String newString;

        try {
            JsonValue parsed = JsonParser.parse(inputJson);
            if (!(parsed instanceof JsonValue.JsonObject obj)) {
                return ToolResult.error("Input must be a JSON object");
            }
            pathStr = obj.getString("path").orElse(null);
            oldString = obj.getString("old_string").orElse(null);
            newString = obj.getString("new_string").orElse(null);
        } catch (Exception e) {
            return ToolResult.error("Failed to parse input: " + e.getMessage());
        }

        if (pathStr == null || pathStr.isBlank()) {
            return ToolResult.error("Missing required field: path");
        }
        if (oldString == null) {
            return ToolResult.error("Missing required field: old_string");
        }
        if (oldString.isEmpty()) {
            return ToolResult.error("old_string must not be empty");
        }
        if (newString == null) {
            return ToolResult.error("Missing required field: new_string");
        }

        Path resolved = resolveSafe(pathStr);
        if (resolved == null) {
            return ToolResult.error("Path traversal detected: access denied");
        }

        if (!Files.exists(resolved)) {
            return ToolResult.error("File not found: " + pathStr);
        }
        if (!Files.isRegularFile(resolved)) {
            return ToolResult.error("Not a regular file: " + pathStr);
        }

        String content;
        try {
            content = Files.readString(resolved);
        } catch (IOException e) {
            return ToolResult.error("Failed to read file: " + e.getMessage());
        }

        int firstIndex = content.indexOf(oldString);
        if (firstIndex == -1) {
            return ToolResult.error("old_string not found in file: " + pathStr);
        }

        int secondIndex = content.indexOf(oldString, firstIndex + oldString.length());
        if (secondIndex != -1) {
            return ToolResult.error(
                    "old_string appears multiple times in file — provide more context to make it unique");
        }

        String updated = content.substring(0, firstIndex)
                + newString
                + content.substring(firstIndex + oldString.length());

        try {
            Files.writeString(resolved, updated);
        } catch (IOException e) {
            return ToolResult.error("Failed to write file: " + e.getMessage());
        }

        return ToolResult.success("Replaced 1 occurrence in " + pathStr);
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
