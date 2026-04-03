package com.agentforge.gateway;

import com.agentforge.common.model.Session;
import com.agentforge.config.loader.AgentForgeConfig;
import com.agentforge.runtime.ConversationRuntime;
import com.agentforge.runtime.TurnResult;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

/**
 * Lightweight HTTP server using Java's built-in {@code com.sun.net.httpserver.HttpServer}.
 *
 * <p>Routes:
 * <ul>
 *   <li>POST /v1/sessions — create new session</li>
 *   <li>GET  /v1/sessions/{id} — get session with history</li>
 *   <li>POST /v1/sessions/{id}/message — send user message (returns TurnResult as JSON)</li>
 *   <li>GET  /v1/sessions/{id}/events — SSE stream of session events</li>
 *   <li>GET  /health — health check</li>
 * </ul>
 */
public final class ForgeServer {

    private static final Logger LOG = Logger.getLogger(ForgeServer.class.getName());

    private final int port;
    private final SessionManager sessionManager;
    private final ForgeBuilder.RuntimeFactory runtimeFactory;
    private final AgentForgeConfig config;

    private HttpServer httpServer;

    ForgeServer(int port, SessionManager sessionManager,
                ForgeBuilder.RuntimeFactory runtimeFactory, AgentForgeConfig config) {
        this.port = port;
        this.sessionManager = sessionManager;
        this.runtimeFactory = runtimeFactory;
        this.config = config;
    }

    /**
     * Start the HTTP server. Uses a virtual-thread executor.
     *
     * @throws IOException if the server cannot bind to the port
     */
    public void start() throws IOException {
        httpServer = HttpServer.create(new InetSocketAddress(port), 0);
        httpServer.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        httpServer.createContext("/health", this::handleHealth);
        httpServer.createContext("/v1/sessions", this::handleSessions);

        httpServer.start();
        LOG.info("ForgeServer started on port " + port);
    }

    /**
     * Stop the HTTP server with a short delay for in-flight requests.
     */
    public void stop() {
        if (httpServer != null) {
            httpServer.stop(1);
            LOG.info("ForgeServer stopped");
        }
    }

    public int port() {
        return port;
    }

    // -------------------------------------------------------------------------
    // Route handlers
    // -------------------------------------------------------------------------

    private void handleHealth(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            return;
        }
        String body = "{\"status\":\"ok\",\"sessions\":" + sessionManager.size() + "}";
        sendResponse(exchange, 200, body);
    }

    private void handleSessions(HttpExchange exchange) throws IOException {
        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod().toUpperCase();

        // POST /v1/sessions — create session
        if ("POST".equals(method) && "/v1/sessions".equals(path)) {
            handleCreateSession(exchange);
            return;
        }

        // Extract session id from path
        String[] parts = path.split("/");
        // Expected: ["", "v1", "sessions", "{id}"] or ["", "v1", "sessions", "{id}", "message"]
        if (parts.length < 4) {
            sendResponse(exchange, 400, "{\"error\":\"Invalid path\"}");
            return;
        }
        String sessionId = parts[3];

        if (parts.length == 4) {
            // GET /v1/sessions/{id}
            if ("GET".equals(method)) {
                handleGetSession(exchange, sessionId);
            } else {
                sendResponse(exchange, 405, "{\"error\":\"Method Not Allowed\"}");
            }
            return;
        }

        if (parts.length == 5) {
            String action = parts[4];
            if ("message".equals(action) && "POST".equals(method)) {
                handleSendMessage(exchange, sessionId);
            } else if ("events".equals(action) && "GET".equals(method)) {
                handleSseEvents(exchange, sessionId);
            } else {
                sendResponse(exchange, 404, "{\"error\":\"Not Found\"}");
            }
            return;
        }

        sendResponse(exchange, 404, "{\"error\":\"Not Found\"}");
    }

    /** POST /v1/sessions */
    private void handleCreateSession(HttpExchange exchange) throws IOException {
        String sessionId = UUID.randomUUID().toString();
        ConversationRuntime runtime = runtimeFactory.create(sessionId);
        SessionManager.SessionEntry entry = sessionManager.create(sessionId, runtime, config.model());

        String body = """
            {"id":"%s","model":"%s","messageCount":0}
            """.formatted(entry.id(), entry.model()).strip();
        sendResponse(exchange, 201, body);
    }

    /** GET /v1/sessions/{id} */
    private void handleGetSession(HttpExchange exchange, String sessionId) throws IOException {
        Optional<SessionManager.SessionEntry> opt = sessionManager.get(sessionId);
        if (opt.isEmpty()) {
            sendResponse(exchange, 404, "{\"error\":\"Session not found: " + sessionId + "\"}");
            return;
        }
        SessionManager.SessionEntry entry = opt.get();
        Session session = entry.session();

        String body = """
            {"id":"%s","model":"%s","messageCount":%d,"createdAt":"%s"}
            """.formatted(
                entry.id(),
                entry.model(),
                session.messageCount(),
                entry.createdAt().toString()
            ).strip();
        sendResponse(exchange, 200, body);
    }

    /** POST /v1/sessions/{id}/message */
    private void handleSendMessage(HttpExchange exchange, String sessionId) throws IOException {
        Optional<SessionManager.SessionEntry> opt = sessionManager.get(sessionId);
        if (opt.isEmpty()) {
            sendResponse(exchange, 404, "{\"error\":\"Session not found: " + sessionId + "\"}");
            return;
        }

        String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        String userMessage = extractMessageField(requestBody);

        if (userMessage == null || userMessage.isBlank()) {
            sendResponse(exchange, 400, "{\"error\":\"Missing 'message' field in request body\"}");
            return;
        }

        SessionManager.SessionEntry entry = opt.get();
        try {
            TurnResult result = entry.runtime().executeTurn(userMessage);
            String body = turnResultToJson(result);
            sendResponse(exchange, 200, body);
        } catch (Exception e) {
            LOG.warning("Error executing turn for session " + sessionId + ": " + e.getMessage());
            sendResponse(exchange, 500, "{\"error\":\"" + escapeJson(e.getMessage()) + "\"}");
        }
    }

    /** GET /v1/sessions/{id}/events — SSE stream */
    private void handleSseEvents(HttpExchange exchange, String sessionId) throws IOException {
        Optional<SessionManager.SessionEntry> opt = sessionManager.get(sessionId);
        if (opt.isEmpty()) {
            sendResponse(exchange, 404, "{\"error\":\"Session not found: " + sessionId + "\"}");
            return;
        }

        SseWriter.setSseHeaders(exchange);
        exchange.sendResponseHeaders(200, 0);

        try (OutputStream out = exchange.getResponseBody()) {
            SseWriter writer = new SseWriter(out);
            // Send a connected event and current session state
            SessionManager.SessionEntry entry = opt.get();
            writer.write("connected", "{\"sessionId\":\"" + sessionId + "\",\"model\":\"" + entry.model() + "\"}");
            writer.writeComment("keep-alive");
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private void sendResponse(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        }
    }

    /**
     * Minimal JSON extraction: reads "message" field value from a flat JSON object.
     * Handles the common case: {"message":"..."} without a full JSON parser.
     */
    static String extractMessageField(String json) {
        if (json == null || json.isBlank()) return null;
        // Look for "message" key
        int keyIdx = json.indexOf("\"message\"");
        if (keyIdx == -1) return null;
        int colonIdx = json.indexOf(':', keyIdx + 9);
        if (colonIdx == -1) return null;
        int start = json.indexOf('"', colonIdx + 1);
        if (start == -1) return null;
        int end = start + 1;
        while (end < json.length()) {
            char c = json.charAt(end);
            if (c == '\\') {
                end += 2;
                continue;
            }
            if (c == '"') break;
            end++;
        }
        if (end >= json.length()) return null;
        return json.substring(start + 1, end)
            .replace("\\n", "\n")
            .replace("\\t", "\t")
            .replace("\\\"", "\"")
            .replace("\\\\", "\\");
    }

    private String turnResultToJson(TurnResult result) {
        StringBuilder sb = new StringBuilder();
        sb.append("{");
        sb.append("\"assistantText\":\"").append(escapeJson(result.assistantText())).append("\",");
        sb.append("\"iterations\":").append(result.iterations()).append(",");
        sb.append("\"wasCompacted\":").append(result.wasCompacted()).append(",");
        sb.append("\"toolCallCount\":").append(result.toolCalls().size()).append(",");
        sb.append("\"inputTokens\":").append(result.turnUsage().inputTokens()).append(",");
        sb.append("\"outputTokens\":").append(result.turnUsage().outputTokens());
        sb.append("}");
        return sb.toString();
    }

    static String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
