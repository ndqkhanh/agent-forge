package com.agentforge.mcp;

import com.agentforge.common.error.McpException;
import com.agentforge.common.json.JsonValue;
import com.agentforge.mcp.transport.McpTransport;
import com.agentforge.tools.Tool;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("McpServerManager")
class McpServerManagerTest {

    /**
     * A fake transport that returns a configurable tools/list response and
     * tracks whether connect/close were called.
     */
    static class FakeTransport implements McpTransport {

        private final String toolsListResponse;
        boolean connectCalled = false;
        boolean closeCalled = false;
        private boolean connected = false;

        FakeTransport(String toolsListResponse) {
            this.toolsListResponse = toolsListResponse;
        }

        @Override
        public void connect() throws McpException {
            connectCalled = true;
            connected = true;
        }

        @Override
        public JsonRpcResponse send(JsonRpcRequest request) throws McpException {
            return JsonRpcResponse.parse(toolsListResponse);
        }

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public void close() {
            closeCalled = true;
            connected = false;
        }
    }

    private static final String TOOLS_RESPONSE_ONE = """
            {"jsonrpc":"2.0","id":"1","result":{"tools":[
              {"name":"list_files","description":"Lists files","inputSchema":{"type":"object","properties":{}}}
            ]}}
            """;

    private static final String TOOLS_RESPONSE_TWO = """
            {"jsonrpc":"2.0","id":"1","result":{"tools":[
              {"name":"read_file","description":"Reads file","inputSchema":{"type":"object","properties":{}}},
              {"name":"write_file","description":"Writes file","inputSchema":{"type":"object","properties":{}}}
            ]}}
            """;

    private static final String TOOLS_RESPONSE_EMPTY = """
            {"jsonrpc":"2.0","id":"1","result":{"tools":[]}}
            """;

    /**
     * Build a McpServerManager using a factory that injects FakeTransports instead
     * of real ones. We do this by using the "stdio" transport path with a known
     * command that the factory will spawn — but we can't inject. Instead, exercise
     * the real bootstrap with an unknown transport type to verify error handling,
     * and test the public API (allTools, toolsForServer, serverNames, close) via
     * a test-only constructor helper using reflection-free approach: we test what
     * bootstrap *returns* by observing method contracts on a live manager built
     * via the cat subprocess approach for lifecycle, and for tool inspection we
     * use the HTTP path which has a trivial connect().
     *
     * For manager API contract tests we use a dedicated in-test subclass approach:
     * we call bootstrap with a real "http" config pointing to an unavailable server
     * — but that would fail at send. So instead we test the manager contract by
     * building it against an actual trivial server.
     *
     * The cleanest approach: expose a package-private factory method in bootstrap
     * that accepts a transport supplier — but since we can't modify production code,
     * we test the two observable failure modes of bootstrap (unknown transport, http
     * connect succeeds but discovery fails) and the lifecycle of a manager built via
     * bootstrap with a real working server.
     *
     * For full API coverage without modifying prod code, we create a small McpServerManager
     * via bootstrap against a real "python3 -c" subprocess that responds to tools/list.
     */

    @Test
    @DisplayName("bootstrap throws McpException for unknown transport type")
    void bootstrap_unknownTransportType_throwsMcpException() {
        McpServerConfig config = new McpServerConfig(
                "bad-server", "grpc", null, null, null, null);

        assertThatThrownBy(() -> McpServerManager.bootstrap(List.of(config)))
                .isInstanceOf(McpException.class)
                .hasMessageContaining("grpc");
    }

    @Test
    @DisplayName("bootstrap with empty config list returns manager with no servers")
    void bootstrap_emptyConfigList_returnsEmptyManager() throws McpException {
        McpServerManager manager = McpServerManager.bootstrap(List.of());

        assertThat(manager.serverNames()).isEmpty();
        assertThat(manager.allTools()).isEmpty();
        manager.close();
    }

    @Test
    @DisplayName("toolsForServer returns empty list for unknown server name")
    void toolsForServer_unknownServer_returnsEmptyList() throws McpException {
        McpServerManager manager = McpServerManager.bootstrap(List.of());

        List<Tool> tools = manager.toolsForServer("nonexistent");

        assertThat(tools).isEmpty();
        manager.close();
    }

    @Test
    @DisplayName("bootstrap with stdio server discovers tools via subprocess")
    void bootstrap_stdioServer_discoversTools() throws Exception {
        // Python3 subprocess: echoes back a valid tools/list response then reads stdin
        String script = "import sys; "
                + "sys.stdout.write('{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"tools\":["
                + "{\"name\":\"my_tool\",\"description\":\"A tool\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":{}}}]}}\\n'); "
                + "sys.stdout.flush(); "
                + "sys.stdin.read()";

        McpServerConfig config = new McpServerConfig(
                "test-server", "stdio", "python3", List.of("-c", script), null, Map.of());

        McpServerManager manager = McpServerManager.bootstrap(List.of(config));
        try {
            assertThat(manager.serverNames()).containsExactly("test-server");
            assertThat(manager.allTools()).hasSize(1);

            Tool tool = manager.allTools().get(0);
            assertThat(tool.name()).isEqualTo("mcp__test_server__my_tool");
            assertThat(tool.description()).isEqualTo("A tool");

            List<Tool> serverTools = manager.toolsForServer("test-server");
            assertThat(serverTools).hasSize(1);
        } finally {
            manager.close();
        }
    }

    @Test
    @DisplayName("allTools aggregates tools from multiple servers")
    void allTools_multipleServers_aggregatesAll() throws Exception {
        String script1 = "import sys; "
                + "sys.stdout.write('{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"tools\":["
                + "{\"name\":\"tool_a\",\"description\":\"A\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":{}}}]}}\\n'); "
                + "sys.stdout.flush(); sys.stdin.read()";
        String script2 = "import sys; "
                + "sys.stdout.write('{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"tools\":["
                + "{\"name\":\"tool_b\",\"description\":\"B\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":{}}},"
                + "{\"name\":\"tool_c\",\"description\":\"C\","
                + "\"inputSchema\":{\"type\":\"object\",\"properties\":{}}}]}}\\n'); "
                + "sys.stdout.flush(); sys.stdin.read()";

        McpServerConfig config1 = new McpServerConfig(
                "server-one", "stdio", "python3", List.of("-c", script1), null, Map.of());
        McpServerConfig config2 = new McpServerConfig(
                "server-two", "stdio", "python3", List.of("-c", script2), null, Map.of());

        McpServerManager manager = McpServerManager.bootstrap(List.of(config1, config2));
        try {
            assertThat(manager.serverNames()).containsExactlyInAnyOrder("server-one", "server-two");
            assertThat(manager.allTools()).hasSize(3);

            assertThat(manager.toolsForServer("server-one")).hasSize(1);
            assertThat(manager.toolsForServer("server-two")).hasSize(2);
        } finally {
            manager.close();
        }
    }

    @Test
    @DisplayName("close is idempotent - second close does not throw")
    void close_calledTwice_doesNotThrow() throws Exception {
        McpServerManager manager = McpServerManager.bootstrap(List.of());

        manager.close();
        manager.close(); // should not throw
    }

    @Test
    @DisplayName("serverNames returns immutable set")
    void serverNames_returnsImmutableOrUnmodifiableSet() throws McpException {
        McpServerManager manager = McpServerManager.bootstrap(List.of());

        Set<String> names = manager.serverNames();
        // Map.copyOf keys are always unmodifiable
        assertThatThrownBy(() -> names.add("extra"))
                .isInstanceOf(UnsupportedOperationException.class);

        manager.close();
    }

    @Test
    @DisplayName("allTools returns unmodifiable list")
    void allTools_returnsUnmodifiableList() throws McpException {
        McpServerManager manager = McpServerManager.bootstrap(List.of());

        List<Tool> tools = manager.allTools();
        assertThatThrownBy(() -> tools.add(null))
                .isInstanceOf(UnsupportedOperationException.class);

        manager.close();
    }
}
