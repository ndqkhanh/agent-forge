package com.agentforge.mcp.transport;

import com.agentforge.common.error.McpException;
import com.agentforge.mcp.JsonRpcRequest;
import com.agentforge.mcp.JsonRpcResponse;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

/**
 * MCP transport over HTTP POST, using JSON-RPC 2.0.
 */
public final class HttpTransport implements McpTransport {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

    private final String url;
    private final HttpClient httpClient;
    private final Map<String, String> headers;
    private volatile boolean connected = false;

    public HttpTransport(String url, Map<String, String> headers) {
        this.url = url;
        this.headers = Map.copyOf(headers);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(DEFAULT_TIMEOUT)
                .build();
    }

    public HttpTransport(String url, HttpClient httpClient, Map<String, String> headers) {
        this.url = url;
        this.httpClient = httpClient;
        this.headers = Map.copyOf(headers);
    }

    @Override
    public void connect() throws McpException {
        connected = true;
    }

    @Override
    public JsonRpcResponse send(JsonRpcRequest request) throws McpException {
        if (!connected) {
            throw new McpException("Transport is not connected");
        }
        String requestBody = request.toJson();

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(DEFAULT_TIMEOUT)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody, StandardCharsets.UTF_8));

        headers.forEach(builder::header);

        HttpRequest httpRequest = builder.build();

        try {
            HttpResponse<String> response = httpClient.send(
                    httpRequest, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            return JsonRpcResponse.parse(response.body());
        } catch (IOException e) {
            throw new McpException("HTTP request failed: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new McpException("HTTP request interrupted", e);
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    @Override
    public void close() {
        connected = false;
    }
}
