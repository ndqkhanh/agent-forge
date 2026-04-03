package com.agentforge.gateway;

import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

/**
 * Helper to write Server-Sent Events (SSE) to an HttpExchange output stream.
 * Format: {@code event: <type>\ndata: <json>\n\n}
 */
public final class SseWriter {

    private final OutputStream out;

    public SseWriter(OutputStream out) {
        if (out == null) throw new IllegalArgumentException("out must not be null");
        this.out = out;
    }

    /**
     * Write a single SSE event.
     *
     * @param eventType the event type (e.g. "message", "text", "done")
     * @param data      the data payload (typically JSON)
     * @throws IOException if writing fails
     */
    public void write(String eventType, String data) throws IOException {
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType must not be blank");
        if (data == null) throw new IllegalArgumentException("data must not be null");

        String sseFrame = "event: " + eventType + "\ndata: " + data + "\n\n";
        out.write(sseFrame.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /**
     * Write a comment line (keep-alive ping).
     *
     * @throws IOException if writing fails
     */
    public void writeComment(String comment) throws IOException {
        String frame = ": " + (comment != null ? comment : "") + "\n\n";
        out.write(frame.getBytes(StandardCharsets.UTF_8));
        out.flush();
    }

    /**
     * Build the SSE frame string without writing, for testing.
     *
     * @param eventType the event type
     * @param data      the data payload
     * @return formatted SSE frame
     */
    public static String formatFrame(String eventType, String data) {
        if (eventType == null || eventType.isBlank()) throw new IllegalArgumentException("eventType must not be blank");
        if (data == null) throw new IllegalArgumentException("data must not be null");
        return "event: " + eventType + "\ndata: " + data + "\n\n";
    }

    /**
     * Set SSE response headers on the exchange before streaming begins.
     *
     * @param exchange the HttpExchange to configure
     */
    public static void setSseHeaders(HttpExchange exchange) {
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.getResponseHeaders().set("X-Accel-Buffering", "no");
    }
}
