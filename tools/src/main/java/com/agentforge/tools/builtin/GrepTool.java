package com.agentforge.tools.builtin;

import com.agentforge.common.json.JsonParser;
import com.agentforge.common.json.JsonValue;
import com.agentforge.tools.Tool;
import com.agentforge.tools.ToolExecutor.ToolResult;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.PathMatcher;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Built-in tool that searches file contents using a regex pattern.
 *
 * <p>Input schema:
 * <pre>{@code {"pattern": "TODO", "path": "src", "include": "*.java"}}</pre>
 *
 * <p>Returns matching lines in {@code file:line: content} format.
 * Searches are restricted to within {@code baseDirectory}.
 */
public final class GrepTool implements Tool {

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "pattern": {
                  "type": "string",
                  "description": "Regular expression pattern to search for"
                },
                "path": {
                  "type": "string",
                  "description": "Directory or file to search in (default: baseDirectory)"
                },
                "include": {
                  "type": "string",
                  "description": "Glob pattern to filter files, e.g. *.java (optional)"
                }
              },
              "required": ["pattern"]
            }
            """;

    private final Path baseDirectory;

    public GrepTool(Path baseDirectory) {
        if (baseDirectory == null) throw new IllegalArgumentException("baseDirectory must not be null");
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "grep";
    }

    @Override
    public String description() {
        return "Search file contents using a regex pattern. Returns matching lines with file:line format.";
    }

    @Override
    public String inputSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(String inputJson) {
        String patternStr;
        String pathStr;
        String include;

        try {
            JsonValue parsed = JsonParser.parse(inputJson);
            if (!(parsed instanceof JsonValue.JsonObject obj)) {
                return ToolResult.error("Input must be a JSON object");
            }
            patternStr = obj.getString("pattern").orElse(null);
            pathStr = obj.getString("path").orElse(null);
            include = obj.getString("include").orElse(null);
        } catch (Exception e) {
            return ToolResult.error("Failed to parse input: " + e.getMessage());
        }

        if (patternStr == null || patternStr.isBlank()) {
            return ToolResult.error("Missing required field: pattern");
        }

        Pattern regex;
        try {
            regex = Pattern.compile(patternStr);
        } catch (PatternSyntaxException e) {
            return ToolResult.error("Invalid regex pattern: " + e.getMessage());
        }

        Path searchRoot = baseDirectory;
        if (pathStr != null && !pathStr.isBlank()) {
            searchRoot = resolveSafe(pathStr);
            if (searchRoot == null) {
                return ToolResult.error("Path traversal detected: access denied");
            }
        }

        if (!Files.exists(searchRoot)) {
            return ToolResult.error("Path not found: " + (pathStr != null ? pathStr : "."));
        }

        PathMatcher fileMatcher = include != null && !include.isBlank()
                ? searchRoot.getFileSystem().getPathMatcher("glob:" + include)
                : null;

        List<String> matches = new ArrayList<>();
        final Path finalSearchRoot = searchRoot;

        try {
            Files.walkFileTree(searchRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    if (!Files.isRegularFile(file)) return FileVisitResult.CONTINUE;

                    // Apply include filter on filename only
                    if (fileMatcher != null && !fileMatcher.matches(file.getFileName())) {
                        return FileVisitResult.CONTINUE;
                    }

                    try {
                        List<String> lines = Files.readAllLines(file);
                        for (int i = 0; i < lines.size(); i++) {
                            Matcher m = regex.matcher(lines.get(i));
                            if (m.find()) {
                                String relativePath = finalSearchRoot.relativize(file).toString();
                                matches.add(relativePath + ":" + (i + 1) + ": " + lines.get(i));
                            }
                        }
                    } catch (IOException ignored) {
                        // Binary or unreadable files are skipped silently
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return ToolResult.error("Search failed: " + e.getMessage());
        }

        if (matches.isEmpty()) {
            return ToolResult.success("No matches found");
        }

        return ToolResult.success(String.join("\n", matches));
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
