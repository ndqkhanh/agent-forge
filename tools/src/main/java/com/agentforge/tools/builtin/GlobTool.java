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
import java.util.Collections;
import java.util.List;

/**
 * Built-in tool that finds files matching a glob pattern.
 *
 * <p>Input schema: {@code {"pattern": "**\/*.java", "path": "src"}}
 *
 * <p>Returns a sorted list of matching file paths relative to the search root.
 * Searches are restricted to within {@code baseDirectory}.
 */
public final class GlobTool implements Tool {

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "pattern": {
                  "type": "string",
                  "description": "Glob pattern to match files, e.g. **/*.java"
                },
                "path": {
                  "type": "string",
                  "description": "Directory to search in (default: baseDirectory)"
                }
              },
              "required": ["pattern"]
            }
            """;

    private final Path baseDirectory;

    public GlobTool(Path baseDirectory) {
        if (baseDirectory == null) throw new IllegalArgumentException("baseDirectory must not be null");
        this.baseDirectory = baseDirectory.toAbsolutePath().normalize();
    }

    @Override
    public String name() {
        return "glob";
    }

    @Override
    public String description() {
        return "Find files matching a glob pattern. Returns sorted list of matching paths.";
    }

    @Override
    public String inputSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(String inputJson) {
        String pattern;
        String pathStr;

        try {
            JsonValue parsed = JsonParser.parse(inputJson);
            if (!(parsed instanceof JsonValue.JsonObject obj)) {
                return ToolResult.error("Input must be a JSON object");
            }
            pattern = obj.getString("pattern").orElse(null);
            pathStr = obj.getString("path").orElse(null);
        } catch (Exception e) {
            return ToolResult.error("Failed to parse input: " + e.getMessage());
        }

        if (pattern == null || pattern.isBlank()) {
            return ToolResult.error("Missing required field: pattern");
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
        if (!Files.isDirectory(searchRoot)) {
            return ToolResult.error("Path is not a directory: " + (pathStr != null ? pathStr : "."));
        }

        // Build two matchers: one for the relative path, one for the absolute path.
        // The absolute-path matcher uses "**/<pattern>" so that top-level files are
        // also matched by "**/*.java" (the leading "**/" covers the base dir prefix).
        PathMatcher relativeMatcher;
        PathMatcher absoluteMatcher;
        try {
            relativeMatcher = searchRoot.getFileSystem().getPathMatcher("glob:" + pattern);
            absoluteMatcher = searchRoot.getFileSystem().getPathMatcher("glob:**/" + pattern);
        } catch (Exception e) {
            return ToolResult.error("Invalid glob pattern: " + e.getMessage());
        }

        List<String> results = new ArrayList<>();
        final Path finalSearchRoot = searchRoot;
        final PathMatcher finalRelative = relativeMatcher;
        final PathMatcher finalAbsolute = absoluteMatcher;

        try {
            Files.walkFileTree(searchRoot, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                    Path relative = finalSearchRoot.relativize(file);
                    // Match on: relative path, filename alone, or absolute path (for ** patterns)
                    if (finalRelative.matches(relative)
                            || finalRelative.matches(file.getFileName())
                            || finalAbsolute.matches(file)) {
                        results.add(relative.toString());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exc) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return ToolResult.error("Glob failed: " + e.getMessage());
        }

        if (results.isEmpty()) {
            return ToolResult.success("No files found matching: " + pattern);
        }

        Collections.sort(results);
        return ToolResult.success(String.join("\n", results));
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
