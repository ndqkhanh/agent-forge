package agentforge.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;

/**
 * Buffered MCP client for speculative execution — intercepts tool calls
 * during speculation and buffers them instead of executing immediately.
 *
 * Analogous to a CPU's reorder buffer: side effects (tool calls) are held
 * in a buffer until the speculation is validated. On commit, buffered calls
 * are flushed (executed for real). On rollback, the buffer is discarded.
 *
 * When not in speculative mode, calls pass through to the real MCPClient.
 */
public final class BufferedMCPClient {

    private static final Logger log = LoggerFactory.getLogger(BufferedMCPClient.class);

    private final MCPClient realClient;

    /** Per-speculation-context buffers: speculationId → list of buffered calls. */
    private final Map<String, List<BufferedCall>> buffers = new ConcurrentHashMap<>();

    /** Currently active speculation context (thread-local for concurrent safety). */
    private volatile String activeSpeculationId = null;

    public BufferedMCPClient(MCPClient realClient) {
        this.realClient = realClient;
    }

    /**
     * Begin buffering tool calls for a speculation context.
     */
    public void beginSpeculation(String speculationId) {
        buffers.putIfAbsent(speculationId, new CopyOnWriteArrayList<>());
        activeSpeculationId = speculationId;
        log.info("Began speculation buffer: {}", speculationId);
    }

    /**
     * Stop buffering for the current speculation context (but don't commit/rollback).
     */
    public void endSpeculation() {
        activeSpeculationId = null;
    }

    /**
     * Whether we're currently in speculative mode.
     */
    public boolean isSpeculating() {
        return activeSpeculationId != null;
    }

    /**
     * Invoke a tool. If speculating, buffers the call and returns a placeholder result.
     * If not speculating, passes through to the real client.
     */
    public CompletableFuture<MCPToolResult> invokeTool(String toolName, Map<String, Object> params) {
        String specId = activeSpeculationId;

        if (specId != null) {
            // Buffer the call
            List<BufferedCall> buffer = buffers.get(specId);
            if (buffer != null) {
                buffer.add(new BufferedCall(toolName, new HashMap<>(params)));
                log.debug("Buffered tool call: {} (speculation: {})", toolName, specId);

                // Return a placeholder result based on params
                String placeholder = params.values().stream()
                        .map(Object::toString)
                        .reduce((a, b) -> a + ", " + b)
                        .orElse("speculative");
                return CompletableFuture.completedFuture(
                        MCPToolResult.success(toolName, "Speculative: " + placeholder));
            }
        }

        // Pass through to real client
        return realClient.invokeTool(toolName, params);
    }

    /**
     * Commit a speculation — flush all buffered calls to the real client.
     *
     * @return results from executing all buffered calls
     */
    public List<MCPToolResult> commit(String speculationId) {
        List<BufferedCall> buffer = buffers.remove(speculationId);
        if (buffer == null || buffer.isEmpty()) {
            return List.of();
        }

        log.info("Committing speculation {}: flushing {} buffered calls",
                speculationId, buffer.size());

        List<MCPToolResult> results = new ArrayList<>();
        for (BufferedCall call : buffer) {
            try {
                MCPToolResult result = realClient.invokeTool(call.toolName(), call.params())
                        .get(30, TimeUnit.SECONDS);
                results.add(result);
            } catch (Exception e) {
                results.add(MCPToolResult.failure(call.toolName(), e.getMessage()));
            }
        }

        return results;
    }

    /**
     * Rollback a speculation — discard all buffered calls without executing.
     */
    public void rollback(String speculationId) {
        List<BufferedCall> buffer = buffers.remove(speculationId);
        int count = buffer != null ? buffer.size() : 0;
        log.info("Rolled back speculation {}: discarded {} buffered calls",
                speculationId, count);
    }

    /**
     * Number of pending (unbuffered) calls for a speculation context.
     */
    public int pendingCallCount(String speculationId) {
        List<BufferedCall> buffer = buffers.get(speculationId);
        return buffer != null ? buffer.size() : 0;
    }

    private record BufferedCall(String toolName, Map<String, Object> params) {}
}
