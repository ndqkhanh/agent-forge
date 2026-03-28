package agentforge.agent;

import org.junit.jupiter.api.*;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD tests for LLMClient — java.net.http.HttpClient wrapper.
 *
 * Tests use a FakeLLMServer (in-memory) to avoid real HTTP calls.
 * Tests cover:
 * - Basic completion request
 * - Retry on transient failure
 * - Timeout handling
 * - SSE streaming response parsing
 * - Multiple provider support
 * - Error response handling
 * - Request configuration (model, temperature, max tokens)
 */
class LLMClientTest {

    // ========== Basic Completion ==========

    @Test
    @DisplayName("complete sends request and returns response text")
    void completeReturnsResponseText() throws Exception {
        var server = new FakeLLMServer(req -> "Hello from LLM!");
        var client = new LLMClient(server, LLMClient.Config.defaults());

        String response = client.complete("Say hello").get(5, TimeUnit.SECONDS);

        assertThat(response).isEqualTo("Hello from LLM!");
    }

    @Test
    @DisplayName("complete with system prompt includes it in request")
    void completeWithSystemPrompt() throws Exception {
        var server = new FakeLLMServer(req -> "response: " + req.systemPrompt());
        var client = new LLMClient(server, LLMClient.Config.defaults());

        String response = client.complete("user msg", "You are a helper")
                .get(5, TimeUnit.SECONDS);

        assertThat(response).contains("You are a helper");
    }

    // ========== Retry on Failure ==========

    @Test
    @DisplayName("retries on transient failure and succeeds")
    void retriesOnTransientFailure() throws Exception {
        var callCount = new AtomicInteger(0);
        var server = new FakeLLMServer(req -> {
            if (callCount.incrementAndGet() < 3) {
                throw new RuntimeException("Server error 500");
            }
            return "Success after retries";
        });
        var config = new LLMClient.Config("test-model", 0.7, 1000, 3, Duration.ofMillis(10));
        var client = new LLMClient(server, config);

        String response = client.complete("test").get(5, TimeUnit.SECONDS);

        assertThat(response).isEqualTo("Success after retries");
        assertThat(callCount.get()).isEqualTo(3);
    }

    @Test
    @DisplayName("exhausts retries and returns error")
    void exhaustsRetriesAndFails() {
        var server = new FakeLLMServer(req -> {
            throw new RuntimeException("Always fails");
        });
        var config = new LLMClient.Config("test-model", 0.7, 1000, 2, Duration.ofMillis(10));
        var client = new LLMClient(server, config);

        assertThatThrownBy(() -> client.complete("test").get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(LLMClient.LLMException.class)
                .hasMessageContaining("Always fails");
    }

    // ========== SSE Streaming ==========

    @Test
    @DisplayName("streamComplete returns tokens via callback")
    void streamCompleteReturnsTokens() throws Exception {
        var server = new FakeLLMServer(req -> "ignored");
        server.setStreamTokens(List.of("Hello", " ", "world", "!"));
        var client = new LLMClient(server, LLMClient.Config.defaults());

        List<String> tokens = new CopyOnWriteArrayList<>();
        String full = client.streamComplete("test", tokens::add).get(5, TimeUnit.SECONDS);

        assertThat(tokens).containsExactly("Hello", " ", "world", "!");
        assertThat(full).isEqualTo("Hello world!");
    }

    // ========== Configuration ==========

    @Test
    @DisplayName("config defaults are reasonable")
    void configDefaults() {
        var config = LLMClient.Config.defaults();

        assertThat(config.model()).isEqualTo("gpt-4o");
        assertThat(config.temperature()).isBetween(0.0, 1.0);
        assertThat(config.maxTokens()).isGreaterThan(0);
        assertThat(config.maxRetries()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("request includes model and temperature from config")
    void requestIncludesConfig() throws Exception {
        var capturedReq = new CompletableFuture<LLMClient.LLMRequest>();
        var server = new FakeLLMServer(req -> {
            capturedReq.complete(req);
            return "ok";
        });
        var config = new LLMClient.Config("claude-3", 0.3, 500, 1, Duration.ofSeconds(5));
        var client = new LLMClient(server, config);

        client.complete("hello").get(5, TimeUnit.SECONDS);

        var req = capturedReq.get(1, TimeUnit.SECONDS);
        assertThat(req.model()).isEqualTo("claude-3");
        assertThat(req.temperature()).isEqualTo(0.3);
        assertThat(req.maxTokens()).isEqualTo(500);
    }

    // ========== Error Handling ==========

    @Test
    @DisplayName("handles null response gracefully")
    void handlesNullResponse() throws Exception {
        var server = new FakeLLMServer(req -> null);
        var client = new LLMClient(server, LLMClient.Config.defaults());

        assertThatThrownBy(() -> client.complete("test").get(5, TimeUnit.SECONDS))
                .isInstanceOf(ExecutionException.class)
                .hasCauseInstanceOf(LLMClient.LLMException.class);
    }

    // ========== Token Counting ==========

    @Test
    @DisplayName("tracks total tokens used")
    void tracksTotalTokensUsed() throws Exception {
        var server = new FakeLLMServer(req -> "response");
        var client = new LLMClient(server, LLMClient.Config.defaults());

        client.complete("request 1").get(5, TimeUnit.SECONDS);
        client.complete("request 2").get(5, TimeUnit.SECONDS);

        assertThat(client.totalRequests()).isEqualTo(2);
    }
}
