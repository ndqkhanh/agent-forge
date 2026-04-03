package com.agentforge.hooks;

import com.agentforge.common.json.JsonValue;
import com.agentforge.common.json.JsonWriter;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Executes a single hook as a subprocess.
 *
 * <p>Protocol:
 * <ul>
 *   <li>Hook receives JSON on stdin: {@code {"tool":"...","input":"...","type":"pre_tool_use"|"post_tool_use"}}
 *   <li>Exit code 0 → ALLOW (stdout is optional modified input/output)
 *   <li>Exit code 2 → DENY  (stdout is reason message)
 *   <li>Any other exit code → ERROR
 *   <li>Timeout → ERROR
 * </ul>
 */
public final class HookRunner {

    private final long timeoutMillis;

    public HookRunner(long timeoutMillis) {
        this.timeoutMillis = timeoutMillis;
    }

    public HookRunner() {
        this(10_000L);
    }

    public HookResult run(HookDefinition hook, String toolName, String payload) {
        if (hook.command() == null || hook.command().isBlank()) {
            return HookResult.error("Hook command is empty");
        }

        String stdinJson = buildStdinJson(hook, toolName, payload);

        ProcessBuilder pb = new ProcessBuilder(List.of("/bin/sh", "-c", hook.command()));
        pb.redirectErrorStream(false);

        Process process;
        try {
            process = pb.start();
        } catch (IOException e) {
            return HookResult.error("Failed to start hook process: " + e.getMessage());
        }

        // Write JSON to stdin in a separate thread to avoid blocking
        Thread stdinWriter = Thread.ofVirtual().start(() -> {
            try (OutputStream os = process.getOutputStream()) {
                os.write(stdinJson.getBytes(StandardCharsets.UTF_8));
            } catch (IOException ignored) {
                // Process may have exited early — not an error we surface
            }
        });

        // Read stdout in a virtual thread so it doesn't block timeout enforcement
        var stdoutHolder = new String[1];
        Thread stdoutReader = Thread.ofVirtual().start(() -> {
            try {
                stdoutHolder[0] = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).strip();
            } catch (IOException e) {
                stdoutHolder[0] = "";
            }
        });

        boolean finished;
        try {
            finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            return HookResult.error("Hook interrupted");
        }

        if (!finished) {
            process.destroyForcibly();
            return HookResult.error("Hook timed out after " + timeoutMillis + "ms");
        }

        // Clean up IO threads
        try {
            stdinWriter.join(200);
            stdoutReader.join(200);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        String stdout = stdoutHolder[0] != null ? stdoutHolder[0] : "";

        int exitCode = process.exitValue();
        return switch (exitCode) {
            case 0 -> stdout.isEmpty()
                    ? HookResult.allow()
                    : HookResult.allowWithModifiedInput(stdout);
            case 2 -> HookResult.deny(stdout.isEmpty() ? "Hook denied execution" : stdout);
            default -> HookResult.error("Hook exited with code " + exitCode
                    + (stdout.isEmpty() ? "" : ": " + stdout));
        };
    }

    private String buildStdinJson(HookDefinition hook, String toolName, String payload) {
        Map<String, JsonValue> fields = new LinkedHashMap<>();
        fields.put("tool", new JsonValue.JsonString(toolName));
        fields.put("input", new JsonValue.JsonString(payload));
        fields.put("type", new JsonValue.JsonString(hookTypeLabel(hook.type())));
        return JsonWriter.write(new JsonValue.JsonObject(fields));
    }

    private static String hookTypeLabel(HookType type) {
        return switch (type) {
            case PRE_TOOL_USE -> "pre_tool_use";
            case POST_TOOL_USE -> "post_tool_use";
        };
    }
}
