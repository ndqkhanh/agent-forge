package com.agentforge.api.stream;

import java.util.ArrayList;
import java.util.List;

/**
 * Incremental Server-Sent Events (SSE) parser.
 *
 * <p>Buffers partial chunks across multiple {@link #feed(String)} calls, correctly
 * handling chunk boundaries that split lines or even field names mid-character.
 * Follows RFC 8895 field parsing semantics.
 *
 * <p><b>Thread safety:</b> NOT thread-safe. Designed for single-threaded use per stream.
 */
public final class SseParser {

    private final StringBuilder buffer = new StringBuilder();

    // Fields accumulated for the current event
    private String currentEvent = null;
    private final StringBuilder currentData = new StringBuilder();
    private String currentId = null;
    private boolean hasData = false;

    /**
     * Feed a raw chunk of text from the HTTP stream.
     *
     * @param chunk raw text chunk (may be partial, may contain multiple events)
     * @return list of complete SSE events parsed from this chunk (may be empty)
     */
    public List<SseEvent> feed(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return List.of();
        }

        buffer.append(chunk);

        List<SseEvent> events = new ArrayList<>();

        // Process complete lines from the buffer
        int start = 0;
        int len = buffer.length();

        while (start < len) {
            // Look for \n or \r\n
            int nlPos = -1;
            boolean crlf = false;
            for (int i = start; i < len; i++) {
                char c = buffer.charAt(i);
                if (c == '\n') {
                    nlPos = i;
                    // Check for preceding \r
                    if (i > start && buffer.charAt(i - 1) == '\r') {
                        crlf = true;
                    }
                    break;
                }
            }

            if (nlPos == -1) {
                // No complete line yet — leave in buffer
                break;
            }

            // Extract the line content (without line ending)
            int lineEnd = crlf ? nlPos - 1 : nlPos;
            String line = buffer.substring(start, lineEnd);
            start = nlPos + 1;

            SseEvent event = processLine(line);
            if (event != null) {
                events.add(event);
            }
        }

        // Remove processed portion from buffer
        if (start > 0) {
            buffer.delete(0, start);
        }

        return events;
    }

    /**
     * Process a single decoded line. Returns a completed SseEvent when an empty
     * line terminates an event that has data; otherwise null.
     */
    private SseEvent processLine(String line) {
        if (line.isEmpty()) {
            // Empty line = dispatch event
            if (hasData) {
                // Remove trailing newline from data per spec
                String data = currentData.length() > 0 && currentData.charAt(currentData.length() - 1) == '\n'
                    ? currentData.substring(0, currentData.length() - 1)
                    : currentData.toString();

                SseEvent event = new SseEvent(currentEvent, data, currentId);
                // Reset for next event (keep last event id per spec)
                currentEvent = null;
                currentData.setLength(0);
                hasData = false;
                // currentId persists across events per SSE spec
                return event;
            }
            // Reset even if no data
            currentEvent = null;
            currentData.setLength(0);
            hasData = false;
            return null;
        }

        // Comment line
        if (line.charAt(0) == ':') {
            return null;
        }

        // Parse field:value
        int colonPos = line.indexOf(':');
        String field;
        String value;

        if (colonPos == -1) {
            // Field with no value = empty string
            field = line;
            value = "";
        } else {
            field = line.substring(0, colonPos);
            // Skip single space after colon per spec
            int valueStart = colonPos + 1;
            if (valueStart < line.length() && line.charAt(valueStart) == ' ') {
                valueStart++;
            }
            value = line.substring(valueStart);
        }

        switch (field) {
            case "event" -> currentEvent = value;
            case "data" -> {
                if (hasData) {
                    currentData.append('\n');
                }
                currentData.append(value);
                hasData = true;
            }
            case "id" -> {
                if (!value.contains("\0")) { // spec: ignore id with null byte
                    currentId = value;
                }
            }
            case "retry" -> {
                // Ignore retry field for now
            }
            default -> {
                // Unknown fields are ignored per SSE spec
            }
        }
        return null;
    }

    /**
     * Reset parser state, discarding any buffered partial data.
     */
    public void reset() {
        buffer.setLength(0);
        currentEvent = null;
        currentData.setLength(0);
        currentId = null;
        hasData = false;
    }

    /**
     * Raw SSE event as parsed from the stream.
     *
     * @param event event type (may be null if no {@code event:} field was present)
     * @param data  event data (joined multi-line data with {@code \n})
     * @param id    last event ID (may be null)
     */
    public record SseEvent(String event, String data, String id) {}
}
