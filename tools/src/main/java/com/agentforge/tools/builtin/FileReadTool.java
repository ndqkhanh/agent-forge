package com.agentforge.tools.builtin;

import com.agentforge.common.json.JsonParser;
import com.agentforge.common.json.JsonValue;
import com.agentforge.tools.Tool;
import com.agentforge.tools.ToolExecutor.ToolResult;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Built-in tool that reads file contents, optionally with offset and line limit.
 *
 * <p>Input schema:
 * <pre>{@code {"path": "src/Main.java", "offset": 0, "limit": 2000}}</pre>
 *
 * <p>Output lines are prefixed with line numbers in {@code cat -n} style.
 * Reads are restricted to within {@code baseDirectory} to prevent path traversal.
 */
public final class FileReadTool implements Tool {

    private static final int DEFAULT_LIMIT = 2000;

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "path": {
                  "type": "string",
                  "description": "Path to the file to read (relative or absolute)"
                },
                "offset": {
                  "type": "number",
                  "description": "Line number to start reading from (0-based, default 0)"
                },
                "limit": {
                  "type": "number",
                  "description": "Maximum number of lines to return (default 2000)"
                }
              },
              "required": ["path"]
            }
            """;

    private final Path baseDirectory;

    public FileReadTool(Path baseDirectory) {
        if (baseDirectory == null) throw new IllegalArgumentException("baseDirectory must not be null");
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "file_read";
    }

    @Override
    public String description() {
        return "Read the contents of a file, with optional line offset and limit. Returns line-numbered output.";
    }

    @Override
    public String inputSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(String inputJson) {
        String pathStr;
        int offset;
        int limit;

        try {
            JsonValue parsed = JsonParser.parse(inputJson);
            if (!(parsed instanceof JsonValue.JsonObject obj)) {
                return ToolResult.error("Input must be a JSON object");
            }
            pathStr = obj.getString("path").orElse(null);
            offset = obj.getNumber("offset").map(Double::intValue).orElse(0);
            limit = obj.getNumber("limit").map(Double::intValue).orElse(DEFAULT_LIMIT);
        } catch (Exception e) {
            return ToolResult.error("Failed to parse input: " + e.getMessage());
        }

        if (pathStr == null || pathStr.isBlank()) {
            return ToolResult.error("Missing required field: path");
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

        List<String> allLines;
        try {
            allLines = Files.readAllLines(resolved);
        } catch (IOException e) {
            return ToolResult.error("Failed to read file: " + e.getMessage());
        }

        int startLine = Math.max(0, offset);
        int endLine = Math.min(allLines.size(), startLine + limit);

        StringBuilder sb = new StringBuilder();
        for (int i = startLine; i < endLine; i++) {
            // cat -n style: right-aligned line number, tab, content (1-based)
            sb.append(String.format("%6d\t%s%n", i + 1, allLines.get(i)));
        }

        return ToolResult.success(sb.toString());
    }

    /**
     * Resolve the given path string relative to baseDirectory and verify it stays within.
     *
     * @return normalized absolute path if safe, null if path traversal detected
     */
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
