package agentforge.agent;

import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD tests for MCPClient — MCP tool discovery and invocation.
 *
 * Tests cover:
 * - Discover available tools from server
 * - Invoke a tool with parameters and get result
 * - Tool not found returns error
 * - Tool invocation failure returns error result
 * - Multiple servers registered
 * - Invocation timeout
 * - Tool call stats tracking
 */
class MCPClientTest {

    private FakeMCPServer fakeServer;
    private MCPClient client;

    @BeforeEach
    void setUp() {
        fakeServer = new FakeMCPServer();
        fakeServer.registerTool(new MCPToolDefinition(
                "web_search", "Search the web",
                Map.of("query", "string")));
        fakeServer.registerTool(new MCPToolDefinition(
                "database_query", "Query a database",
                Map.of("sql", "string")));

        client = new MCPClient();
        client.addServer("default", fakeServer);
    }

    // ========== Tool Discovery ==========

    @Test
    @DisplayName("discoverTools returns all available tools")
    void discoverToolsReturnsAll() {
        List<MCPToolDefinition> tools = client.discoverTools();

        assertThat(tools).hasSize(2);
        assertThat(tools).extracting(MCPToolDefinition::name)
                .containsExactlyInAnyOrder("web_search", "database_query");
    }

    @Test
    @DisplayName("discoverTools with no servers returns empty")
    void discoverToolsEmptyWithNoServers() {
        var emptyClient = new MCPClient();
        assertThat(emptyClient.discoverTools()).isEmpty();
    }

    // ========== Tool Invocation ==========

    @Test
    @DisplayName("invokeTool calls tool and returns result")
    void invokeToolReturnsResult() throws Exception {
        fakeServer.setHandler("web_search", params ->
                MCPToolResult.success("web_search", "Results for: " + params.get("query")));

        MCPToolResult result = client.invokeTool("web_search",
                Map.of("query", "Java 21")).get(5, TimeUnit.SECONDS);

        assertThat(result.success()).isTrue();
        assertThat(result.output()).contains("Java 21");
    }

    @Test
    @DisplayName("invokeTool with unknown tool returns error")
    void invokeUnknownToolReturnsError() throws Exception {
        MCPToolResult result = client.invokeTool("nonexistent",
                Map.of()).get(5, TimeUnit.SECONDS);

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("not found");
    }

    @Test
    @DisplayName("invokeTool handles server failure gracefully")
    void invokeToolHandlesServerFailure() throws Exception {
        fakeServer.setHandler("web_search", params -> {
            throw new RuntimeException("Server crashed");
        });

        MCPToolResult result = client.invokeTool("web_search",
                Map.of("query", "test")).get(5, TimeUnit.SECONDS);

        assertThat(result.success()).isFalse();
        assertThat(result.error()).contains("Server crashed");
    }

    // ========== Multiple Servers ==========

    @Test
    @DisplayName("tools from multiple servers are all discoverable")
    void multipleServersDiscoverable() {
        var server2 = new FakeMCPServer();
        server2.registerTool(new MCPToolDefinition(
                "code_execute", "Execute code", Map.of("code", "string")));
        client.addServer("code", server2);

        List<MCPToolDefinition> tools = client.discoverTools();
        assertThat(tools).hasSize(3);
        assertThat(tools).extracting(MCPToolDefinition::name)
                .contains("web_search", "database_query", "code_execute");
    }

    @Test
    @DisplayName("invokeTool routes to correct server")
    void invokeToolRoutesToCorrectServer() throws Exception {
        var server2 = new FakeMCPServer();
        server2.registerTool(new MCPToolDefinition(
                "code_execute", "Execute code", Map.of()));
        server2.setHandler("code_execute", params ->
                MCPToolResult.success("code_execute", "executed!"));
        client.addServer("code", server2);

        MCPToolResult result = client.invokeTool("code_execute",
                Map.of()).get(5, TimeUnit.SECONDS);

        assertThat(result.success()).isTrue();
        assertThat(result.output()).isEqualTo("executed!");
    }

    // ========== Stats ==========

    @Test
    @DisplayName("totalInvocations tracks tool calls")
    void totalInvocationsTracksCalls() throws Exception {
        fakeServer.setHandler("web_search", params ->
                MCPToolResult.success("web_search", "ok"));

        client.invokeTool("web_search", Map.of("query", "a")).get(5, TimeUnit.SECONDS);
        client.invokeTool("web_search", Map.of("query", "b")).get(5, TimeUnit.SECONDS);

        assertThat(client.totalInvocations()).isEqualTo(2);
    }

    @Test
    @DisplayName("serverCount returns number of registered servers")
    void serverCountReturnsRegistered() {
        assertThat(client.serverCount()).isEqualTo(1);

        client.addServer("another", new FakeMCPServer());
        assertThat(client.serverCount()).isEqualTo(2);
    }
}
