package com.agentforge.mcp.discovery;

import com.agentforge.common.error.McpException;
import com.agentforge.common.model.ToolSchema;
import com.agentforge.mcp.JsonRpcRequest;
import com.agentforge.mcp.JsonRpcResponse;
import com.agentforge.mcp.transport.McpTransport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("McpToolDiscovery")
class McpToolDiscoveryTest {

    private static McpTransport transportReturning(String responseJson) {
        return new McpTransport() {
            @Override
            public JsonRpcResponse send(JsonRpcRequest request) throws McpException {
                return JsonRpcResponse.parse(responseJson);
            }
            @Override public boolean isConnected() { return true; }
            @Override public void connect() {}
            @Override public void close() {}
        };
    }

    private static McpTransport transportThrowingOn(String errorMsg) {
        return new McpTransport() {
            @Override
            public JsonRpcResponse send(JsonRpcRequest request) throws McpException {
                throw new McpException(errorMsg);
            }
            @Override public boolean isConnected() { return false; }
            @Override public void connect() {}
            @Override public void close() {}
        };
    }

    @Test
    @DisplayName("discoverTools returns tools from valid tools/list response")
    void discoverTools_validResponse_returnsTools() throws McpException {
        String response = """
                {"jsonrpc":"2.0","id":"1","result":{"tools":[
                  {"name":"list_files","description":"Lists files","inputSchema":{"type":"object","properties":{}}},
                  {"name":"read_file","description":"Reads a file","inputSchema":{"type":"object","properties":{"path":{"type":"string"}}}}
                ]}}
                """;
        McpToolDiscovery discovery = new McpToolDiscovery(transportReturning(response));

        List<ToolSchema> schemas = discovery.discoverTools();

        assertThat(schemas).hasSize(2);
        assertThat(schemas.get(0).name()).isEqualTo("list_files");
        assertThat(schemas.get(0).description()).isEqualTo("Lists files");
        assertThat(schemas.get(1).name()).isEqualTo("read_file");
    }

    @Test
    @DisplayName("discoverTools returns empty list when tools array is empty")
    void discoverTools_emptyToolsArray_returnsEmptyList() throws McpException {
        String response = """
                {"jsonrpc":"2.0","id":"1","result":{"tools":[]}}
                """;
        McpToolDiscovery discovery = new McpToolDiscovery(transportReturning(response));

        List<ToolSchema> schemas = discovery.discoverTools();

        assertThat(schemas).isEmpty();
    }

    @Test
    @DisplayName("discoverTools skips tools with blank name")
    void discoverTools_blankToolName_isSkipped() throws McpException {
        String response = """
                {"jsonrpc":"2.0","id":"1","result":{"tools":[
                  {"name":"","description":"Invalid","inputSchema":{}},
                  {"name":"valid_tool","description":"Valid","inputSchema":{}}
                ]}}
                """;
        McpToolDiscovery discovery = new McpToolDiscovery(transportReturning(response));

        List<ToolSchema> schemas = discovery.discoverTools();

        assertThat(schemas).hasSize(1);
        assertThat(schemas.get(0).name()).isEqualTo("valid_tool");
    }

    @Test
    @DisplayName("discoverTools uses default inputSchema when not present")
    void discoverTools_missingInputSchema_usesDefault() throws McpException {
        String response = """
                {"jsonrpc":"2.0","id":"1","result":{"tools":[
                  {"name":"my_tool","description":"A tool"}
                ]}}
                """;
        McpToolDiscovery discovery = new McpToolDiscovery(transportReturning(response));

        List<ToolSchema> schemas = discovery.discoverTools();

        assertThat(schemas).hasSize(1);
        assertThat(schemas.get(0).inputSchemaJson())
                .isEqualTo("{\"type\":\"object\",\"properties\":{}}");
    }

    @Test
    @DisplayName("discoverTools uses empty description when missing")
    void discoverTools_missingDescription_usesEmpty() throws McpException {
        String response = """
                {"jsonrpc":"2.0","id":"1","result":{"tools":[
                  {"name":"no_desc_tool","inputSchema":{}}
                ]}}
                """;
        McpToolDiscovery discovery = new McpToolDiscovery(transportReturning(response));

        List<ToolSchema> schemas = discovery.discoverTools();

        assertThat(schemas).hasSize(1);
        assertThat(schemas.get(0).description()).isEmpty();
    }

    @Test
    @DisplayName("discoverTools throws McpException on error response")
    void discoverTools_errorResponse_throwsMcpException() {
        String response = """
                {"jsonrpc":"2.0","id":"1","error":{"code":-32601,"message":"Method not found"}}
                """;
        McpToolDiscovery discovery = new McpToolDiscovery(transportReturning(response));

        assertThatThrownBy(discovery::discoverTools)
                .isInstanceOf(McpException.class)
                .hasMessageContaining("Method not found");
    }

    @Test
    @DisplayName("discoverTools throws McpException when result is not object")
    void discoverTools_resultNotObject_throwsMcpException() {
        String response = """
                {"jsonrpc":"2.0","id":"1","result":"not-an-object"}
                """;
        McpToolDiscovery discovery = new McpToolDiscovery(transportReturning(response));

        assertThatThrownBy(discovery::discoverTools)
                .isInstanceOf(McpException.class)
                .hasMessageContaining("JSON object");
    }

    @Test
    @DisplayName("discoverTools propagates transport McpException")
    void discoverTools_transportThrows_propagatesMcpException() {
        McpToolDiscovery discovery = new McpToolDiscovery(
                transportThrowingOn("network failure"));

        assertThatThrownBy(discovery::discoverTools)
                .isInstanceOf(McpException.class)
                .hasMessageContaining("network failure");
    }

    @Test
    @DisplayName("discoverTools sends tools/list method")
    void discoverTools_sendsToolsListMethod() throws McpException {
        String[] capturedMethod = {null};
        McpTransport capturingTransport = new McpTransport() {
            @Override
            public JsonRpcResponse send(JsonRpcRequest request) throws McpException {
                capturedMethod[0] = request.method();
                return JsonRpcResponse.parse(
                        "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":{\"tools\":[]}}");
            }
            @Override public boolean isConnected() { return true; }
            @Override public void connect() {}
            @Override public void close() {}
        };
        McpToolDiscovery discovery = new McpToolDiscovery(capturingTransport);

        discovery.discoverTools();

        assertThat(capturedMethod[0]).isEqualTo("tools/list");
    }

    @Test
    @DisplayName("discoverTools result list is unmodifiable")
    void discoverTools_result_isUnmodifiable() throws McpException {
        String response = """
                {"jsonrpc":"2.0","id":"1","result":{"tools":[
                  {"name":"tool","description":"desc","inputSchema":{}}
                ]}}
                """;
        McpToolDiscovery discovery = new McpToolDiscovery(transportReturning(response));

        List<ToolSchema> schemas = discovery.discoverTools();

        assertThatThrownBy(() -> schemas.add(ToolSchema.noParams("x", "y")))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
