package agentforge.agent;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD tests for BufferedMCPClient — buffers tool calls during speculation.
 *
 * Tests cover:
 * - Non-speculative mode passes through to real MCPClient
 * - Speculative mode buffers calls (no real invocation)
 * - Commit flushes buffer (executes all buffered calls)
 * - Rollback discards buffer (nothing executed)
 * - Buffer tracks pending call count
 * - Multiple buffers for different speculation contexts
 * - Buffered calls return predicted results
 */
class BufferedMCPClientTest {

    private FakeMCPServer fakeServer;
    private MCPClient realClient;
    private BufferedMCPClient bufferedClient;
    private AtomicInteger realInvocationCount;

    @BeforeEach
    void setUp() {
        fakeServer = new FakeMCPServer();
        fakeServer.registerTool(new MCPToolDefinition(
                "web_search", "Search the web", Map.of("query", "string")));
        realInvocationCount = new AtomicInteger(0);
        fakeServer.setHandler("web_search", params -> {
            realInvocationCount.incrementAndGet();
            return MCPToolResult.success("web_search", "Results for: " + params.get("query"));
        });

        realClient = new MCPClient();
        realClient.addServer("default", fakeServer);

        bufferedClient = new BufferedMCPClient(realClient);
    }

    // ========== Pass-Through Mode ==========

    @Test
    @DisplayName("non-speculative invocation passes through to real client")
    void nonSpeculativePassesThrough() throws Exception {
        MCPToolResult result = bufferedClient.invokeTool("web_search",
                Map.of("query", "Java 21")).get(5, TimeUnit.SECONDS);

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("Java 21");
        assertThat(realInvocationCount.get()).isEqualTo(1);
    }

    // ========== Speculative Buffering ==========

    @Test
    @DisplayName("speculative invocation buffers call without executing")
    void speculativeBuffersWithoutExecuting() throws Exception {
        bufferedClient.beginSpeculation("spec-1");

        MCPToolResult result = bufferedClient.invokeTool("web_search",
                Map.of("query", "buffered")).get(5, TimeUnit.SECONDS);

        // Returns a placeholder result
        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("buffered");

        // Real server was NOT called
        assertThat(realInvocationCount.get()).isEqualTo(0);
        assertThat(bufferedClient.pendingCallCount("spec-1")).isEqualTo(1);
    }

    @Test
    @DisplayName("multiple buffered calls accumulate in buffer")
    void multipleBufferedCallsAccumulate() throws Exception {
        bufferedClient.beginSpeculation("spec-1");

        bufferedClient.invokeTool("web_search", Map.of("query", "q1")).get(5, TimeUnit.SECONDS);
        bufferedClient.invokeTool("web_search", Map.of("query", "q2")).get(5, TimeUnit.SECONDS);
        bufferedClient.invokeTool("web_search", Map.of("query", "q3")).get(5, TimeUnit.SECONDS);

        assertThat(bufferedClient.pendingCallCount("spec-1")).isEqualTo(3);
        assertThat(realInvocationCount.get()).isEqualTo(0);
    }

    // ========== Commit ==========

    @Test
    @DisplayName("commit flushes buffer and executes all buffered calls")
    void commitFlushesBuffer() throws Exception {
        bufferedClient.beginSpeculation("spec-1");

        bufferedClient.invokeTool("web_search", Map.of("query", "q1")).get(5, TimeUnit.SECONDS);
        bufferedClient.invokeTool("web_search", Map.of("query", "q2")).get(5, TimeUnit.SECONDS);

        List<MCPToolResult> results = bufferedClient.commit("spec-1");

        assertThat(results).hasSize(2);
        assertThat(results).allSatisfy(r -> assertThat(r.success()).isTrue());
        assertThat(realInvocationCount.get()).isEqualTo(2);
        assertThat(bufferedClient.pendingCallCount("spec-1")).isEqualTo(0);
    }

    // ========== Rollback ==========

    @Test
    @DisplayName("rollback discards buffer without executing")
    void rollbackDiscardsBuffer() throws Exception {
        bufferedClient.beginSpeculation("spec-1");

        bufferedClient.invokeTool("web_search", Map.of("query", "q1")).get(5, TimeUnit.SECONDS);
        bufferedClient.invokeTool("web_search", Map.of("query", "q2")).get(5, TimeUnit.SECONDS);

        bufferedClient.rollback("spec-1");

        assertThat(realInvocationCount.get()).isEqualTo(0);
        assertThat(bufferedClient.pendingCallCount("spec-1")).isEqualTo(0);
    }

    // ========== Multiple Speculation Contexts ==========

    @Test
    @DisplayName("independent speculation contexts don't interfere")
    void independentContextsDontInterfere() throws Exception {
        // Buffer call under spec-1
        bufferedClient.beginSpeculation("spec-1");
        bufferedClient.invokeTool("web_search", Map.of("query", "s1")).get(5, TimeUnit.SECONDS);

        // Switch to spec-2 and buffer another call
        bufferedClient.beginSpeculation("spec-2");
        bufferedClient.invokeTool("web_search", Map.of("query", "s2")).get(5, TimeUnit.SECONDS);

        assertThat(bufferedClient.pendingCallCount("spec-1")).isEqualTo(1);
        assertThat(bufferedClient.pendingCallCount("spec-2")).isEqualTo(1);

        // Commit spec-1, rollback spec-2
        bufferedClient.commit("spec-1");
        bufferedClient.rollback("spec-2");

        assertThat(realInvocationCount.get()).isEqualTo(1);
    }

    // ========== Stats ==========

    @Test
    @DisplayName("isSpeculating returns correct state")
    void isSpeculatingReturnsCorrectState() {
        assertThat(bufferedClient.isSpeculating()).isFalse();

        bufferedClient.beginSpeculation("spec-1");
        assertThat(bufferedClient.isSpeculating()).isTrue();

        bufferedClient.endSpeculation();
        assertThat(bufferedClient.isSpeculating()).isFalse();
    }
}
