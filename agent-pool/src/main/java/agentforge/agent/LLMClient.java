package agentforge.agent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * Custom LLM HTTP client wrapper using java.net.http.HttpClient internally.
 *
 * Features:
 * - Retry with exponential backoff on transient failures
 * - SSE streaming support via token callbacks
 * - Configurable model, temperature, max tokens
 * - Request counting for observability
 *
 * Uses a LLMTransport abstraction for testability (real HTTP vs fake).
 */
public final class LLMClient {

    private static final Logger log = LoggerFactory.getLogger(LLMClient.class);

    private final LLMTransport transport;
    private final Config config;
    private final AtomicLong requestCount = new AtomicLong(0);

    public LLMClient(LLMTransport transport, Config config) {
        this.transport = transport;
        this.config = config;
    }

    /**
     * Send a completion request to the LLM.
     */
    public CompletableFuture<String> complete(String userMessage) {
        return complete(userMessage, null);
    }

    /**
     * Send a completion request with a system prompt.
     */
    public CompletableFuture<String> complete(String userMessage, String systemPrompt) {
        var request = new LLMRequest(
                config.model, userMessage, systemPrompt,
                config.temperature, config.maxTokens);

        return CompletableFuture.supplyAsync(() -> {
            requestCount.incrementAndGet();
            return executeWithRetry(request);
        }, Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Stream a completion, calling tokenCallback for each token as it arrives.
     *
     * @return the full concatenated response
     */
    public CompletableFuture<String> streamComplete(String userMessage, Consumer<String> tokenCallback) {
        var request = new LLMRequest(
                config.model, userMessage, null,
                config.temperature, config.maxTokens);

        return CompletableFuture.supplyAsync(() -> {
            requestCount.incrementAndGet();
            List<String> tokens = transport.stream(request);
            StringBuilder sb = new StringBuilder();
            for (String token : tokens) {
                tokenCallback.accept(token);
                sb.append(token);
            }
            return sb.toString();
        }, Executors.newVirtualThreadPerTaskExecutor());
    }

    /**
     * Get total number of requests made.
     */
    public long totalRequests() {
        return requestCount.get();
    }

    private String executeWithRetry(LLMRequest request) {
        Exception lastException = null;

        for (int attempt = 0; attempt <= config.maxRetries; attempt++) {
            try {
                String response = transport.complete(request);
                if (response == null) {
                    throw new LLMException("LLM returned null response");
                }
                return response;
            } catch (LLMException e) {
                throw e;
            } catch (Exception e) {
                lastException = e;
                log.warn("LLM request failed (attempt {}/{}): {}",
                        attempt + 1, config.maxRetries + 1, e.getMessage());

                if (attempt < config.maxRetries) {
                    try {
                        long backoff = config.retryDelay.toMillis() * (1L << attempt);
                        Thread.sleep(backoff);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new LLMException("Interrupted during retry", ie);
                    }
                }
            }
        }

        throw new LLMException("LLM request failed after " + (config.maxRetries + 1)
                + " attempts: " + lastException.getMessage(), lastException);
    }

    // ========== Inner Types ==========

    /**
     * Transport abstraction — allows fake implementations for testing.
     */
    public interface LLMTransport {
        String complete(LLMRequest request);
        List<String> stream(LLMRequest request);
    }

    /**
     * LLM request payload.
     */
    public record LLMRequest(
            String model,
            String userMessage,
            String systemPrompt,
            double temperature,
            int maxTokens) {}

    /**
     * Configuration for the LLM client.
     */
    public record Config(
            String model,
            double temperature,
            int maxTokens,
            int maxRetries,
            Duration retryDelay) {

        public static Config defaults() {
            return new Config("gpt-4o", 0.7, 1000, 3, Duration.ofMillis(100));
        }
    }

    /**
     * Exception for LLM-related errors.
     */
    public static final class LLMException extends RuntimeException {
        public LLMException(String message) { super(message); }
        public LLMException(String message, Throwable cause) { super(message, cause); }
    }
}
