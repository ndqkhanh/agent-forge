package com.agentforge.mcp.transport;

import com.agentforge.common.error.McpException;
import com.agentforge.mcp.JsonRpcRequest;
import com.agentforge.mcp.JsonRpcResponse;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * MCP transport over a subprocess's stdin/stdout using JSON-RPC 2.0.
 */
public final class StdioTransport implements McpTransport {

    private final String command;
    private final List<String> args;
    private final Map<String, String> env;

    private Process process;
    private BufferedReader reader;
    private BufferedWriter writer;
    private volatile boolean connected = false;

    public StdioTransport(String command, List<String> args, Map<String, String> env) {
        this.command = command;
        this.args = List.copyOf(args);
        this.env = Map.copyOf(env);
    }

    @Override
    public void connect() throws McpException {
        try {
            List<String> fullCommand = buildCommand();
            ProcessBuilder pb = new ProcessBuilder(fullCommand);
            pb.environment().putAll(env);
            pb.redirectErrorStream(false);
            process = pb.start();
            reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
            writer = new BufferedWriter(
                    new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            connected = true;
        } catch (IOException e) {
            throw new McpException("Failed to start subprocess: " + command, e);
        }
    }

    @Override
    public JsonRpcResponse send(JsonRpcRequest request) throws McpException {
        if (!connected) {
            throw new McpException("Transport is not connected");
        }
        try {
            String json = request.toJson();
            writer.write(json);
            writer.newLine();
            writer.flush();

            String responseLine = reader.readLine();
            if (responseLine == null) {
                throw new McpException("Subprocess closed stdout unexpectedly");
            }
            return JsonRpcResponse.parse(responseLine);
        } catch (IOException e) {
            throw new McpException("I/O error communicating with subprocess", e);
        }
    }

    @Override
    public boolean isConnected() {
        return connected && process != null && process.isAlive();
    }

    @Override
    public void close() {
        connected = false;
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException ignored) {}
        }
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException ignored) {}
        }
        if (process != null) {
            process.destroy();
            try {
                process.waitFor();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }

    private List<String> buildCommand() {
        if (args.isEmpty()) {
            return List.of(command);
        }
        List<String> cmd = new java.util.ArrayList<>();
        cmd.add(command);
        cmd.addAll(args);
        return cmd;
    }
}
