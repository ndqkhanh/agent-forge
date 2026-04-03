package com.agentforge.tools.builtin;

import com.agentforge.common.json.JsonParser;
import com.agentforge.common.json.JsonValue;
import com.agentforge.tools.Tool;
import com.agentforge.tools.ToolExecutor.ToolResult;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Built-in tool that executes shell commands via ProcessBuilder.
 *
 * <p>Input schema: {@code {"command": "echo hello"}}
 *
 * <p>stdout and stderr are both captured and returned. The process is killed
 * after {@code timeoutMillis} milliseconds.
 */
public final class BashTool implements Tool {

    private static final long DEFAULT_TIMEOUT_MILLIS = 120_000L;

    private static final String SCHEMA = """
            {
              "type": "object",
              "properties": {
                "command": {
                  "type": "string",
                  "description": "The shell command to execute"
                }
              },
              "required": ["command"]
            }
            """;

    private final Path workingDirectory;
    private final long timeoutMillis;

    public BashTool(Path workingDirectory) {
        this(workingDirectory, DEFAULT_TIMEOUT_MILLIS);
    }

    public BashTool(Path workingDirectory, long timeoutMillis) {
        if (workingDirectory == null) throw new IllegalArgumentException("workingDirectory must not be null");
        if (timeoutMillis <= 0) throw new IllegalArgumentException("timeoutMillis must be positive");
        this.workingDirectory = workingDirectory;
        this.timeoutMillis = timeoutMillis;
    }

    @Override
    public String name() {
        return "bash";
    }

    @Override
    public String description() {
        return "Execute a shell command and return its output (stdout + stderr).";
    }

    @Override
    public String inputSchema() {
        return SCHEMA;
    }

    @Override
    public ToolResult execute(String inputJson) {
        String command;
        try {
            JsonValue parsed = JsonParser.parse(inputJson);
            if (!(parsed instanceof JsonValue.JsonObject obj)) {
                return ToolResult.error("Input must be a JSON object");
            }
            command = obj.getString("command").orElse(null);
        } catch (Exception e) {
            return ToolResult.error("Failed to parse input: " + e.getMessage());
        }

        if (command == null || command.isBlank()) {
            return ToolResult.error("Missing required field: command");
        }

        ProcessBuilder pb = new ProcessBuilder(List.of("/bin/sh", "-c", command));
        pb.directory(workingDirectory.toFile());
        pb.redirectErrorStream(true); // merge stderr into stdout

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            return ToolResult.error("Failed to start process: " + e.getMessage());
        }

        // Capture output on a virtual thread so we don't block the main thread
        final Process finalProcess = process;
        StringBuilder output = new StringBuilder();
        Thread readerThread = Thread.ofVirtual().start(() -> {
            try (InputStream is = finalProcess.getInputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = is.read(buf)) != -1) {
                    output.append(new String(buf, 0, n, StandardCharsets.UTF_8));
                }
            } catch (IOException ignored) {
            }
        });

        boolean finished;
        try {
            finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return ToolResult.error("Interrupted while waiting for process");
        }

        if (!finished) {
            process.destroyForcibly();
            return ToolResult.error("Command timed out after " + timeoutMillis + "ms");
        }

        try {
            readerThread.join(5_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        int exitCode = process.exitValue();
        String result = output.toString();
        if (exitCode != 0) {
            return ToolResult.error("Command exited with code " + exitCode + ":\n" + result);
        }
        return ToolResult.success(result);
    }
}
