package com.agentforge.mcp.discovery;

import com.agentforge.common.error.McpException;
import com.agentforge.common.model.ToolSchema;
import com.agentforge.mcp.JsonRpcResponse;
import com.agentforge.mcp.transport.McpTransport;
import com.agentforge.tools.ToolExecutor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("McpTool")
class McpToolTest {

    private static McpTransport successTransport(String resultJson) {
        return new McpTransport() {
            @Override
            public JsonRpcResponse send(com.agentforge.mcp.JsonRpcRequest request) throws McpException {
                return JsonRpcResponse.parse(
                        "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":" + resultJson + "}");
            }
            @Override public boolean isConnected() { return true; }
            @Override public void connect() {}
            @Override public void close() {}
        };
    }

    private static McpTransport errorTransport(String errorMessage) {
        return new McpTransport() {
            @Override
            public JsonRpcResponse send(com.agentforge.mcp.JsonRpcRequest request) throws McpException {
                return JsonRpcResponse.parse(
                        "{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"error\":{\"code\":-1,\"message\":\""
                                + errorMessage + "\"}}");
            }
            @Override public boolean isConnected() { return true; }
            @Override public void connect() {}
            @Override public void close() {}
        };
    }

    private static McpTransport throwingTransport() {
        return new McpTransport() {
            @Override
            public JsonRpcResponse send(com.agentforge.mcp.JsonRpcRequest request) throws McpException {
                throw new McpException("connection lost");
            }
            @Override public boolean isConnected() { return false; }
            @Override public void connect() {}
            @Override public void close() {}
        };
    }

    @Test
    @DisplayName("name follows mcp__server__tool convention")
    void name_followsNamingConvention() {
        ToolSchema schema = ToolSchema.noParams("list_files", "lists files");
        McpTool tool = new McpTool(schema, successTransport("{}"), "my-server");

        assertThat(tool.name()).isEqualTo("mcp__my_server__list_files");
    }

    @Test
    @DisplayName("name normalizes special characters in server name")
    void name_normalizeSpecialChars_serverName() {
        ToolSchema schema = ToolSchema.noParams("tool", "desc");
        McpTool tool = new McpTool(schema, successTransport("{}"), "my.server-v2");

        assertThat(tool.name()).isEqualTo("mcp__my_server_v2__tool");
    }

    @Test
    @DisplayName("name normalizes special characters in tool name")
    void name_normalizeSpecialChars_toolName() {
        ToolSchema schema = ToolSchema.noParams("get-resource.data", "desc");
        McpTool tool = new McpTool(schema, successTransport("{}"), "server");

        assertThat(tool.name()).isEqualTo("mcp__server__get_resource_data");
    }

    @Test
    @DisplayName("name is all lowercase")
    void name_isLowercase() {
        ToolSchema schema = ToolSchema.noParams("GetData", "desc");
        McpTool tool = new McpTool(schema, successTransport("{}"), "MyServer");

        assertThat(tool.name()).isEqualTo("mcp__myserver__getdata");
    }

    @Test
    @DisplayName("description delegates to schema")
    void description_delegatesToSchema() {
        ToolSchema schema = new ToolSchema("tool", "A helpful tool", "{}");
        McpTool tool = new McpTool(schema, successTransport("{}"), "srv");

        assertThat(tool.description()).isEqualTo("A helpful tool");
    }

    @Test
    @DisplayName("inputSchema delegates to schema")
    void inputSchema_delegatesToSchema() {
        String schemaJson = "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}}}";
        ToolSchema schema = new ToolSchema("tool", "desc", schemaJson);
        McpTool tool = new McpTool(schema, successTransport("{}"), "srv");

        assertThat(tool.inputSchema()).isEqualTo(schemaJson);
    }

    @Test
    @DisplayName("execute returns success result on valid response")
    void execute_validResponse_returnsSuccess() {
        ToolSchema schema = ToolSchema.noParams("list_files", "lists files");
        McpTool tool = new McpTool(schema, successTransport("{\"files\":[]}"), "srv");

        ToolExecutor.ToolResult result = tool.execute("{\"path\":\"/tmp\"}");

        assertThat(result.isError()).isFalse();
        assertThat(result.output()).isNotNull();
    }

    @Test
    @DisplayName("execute with null inputJson uses empty object")
    void execute_nullInput_usesEmptyObject() {
        ToolSchema schema = ToolSchema.noParams("ping", "pings");
        McpTool tool = new McpTool(schema, successTransport("\"pong\""), "srv");

        ToolExecutor.ToolResult result = tool.execute(null);

        assertThat(result.isError()).isFalse();
    }

    @Test
    @DisplayName("execute with blank inputJson uses empty object")
    void execute_blankInput_usesEmptyObject() {
        ToolSchema schema = ToolSchema.noParams("ping", "pings");
        McpTool tool = new McpTool(schema, successTransport("\"pong\""), "srv");

        ToolExecutor.ToolResult result = tool.execute("   ");

        assertThat(result.isError()).isFalse();
    }

    @Test
    @DisplayName("execute returns error result on MCP error response")
    void execute_mcpError_returnsErrorResult() {
        ToolSchema schema = ToolSchema.noParams("tool", "desc");
        McpTool tool = new McpTool(schema, errorTransport("Tool not found"), "srv");

        ToolExecutor.ToolResult result = tool.execute("{}");

        assertThat(result.isError()).isTrue();
        assertThat(result.output()).contains("Tool not found");
    }

    @Test
    @DisplayName("execute returns error result on transport exception")
    void execute_transportThrows_returnsErrorResult() {
        ToolSchema schema = ToolSchema.noParams("tool", "desc");
        McpTool tool = new McpTool(schema, throwingTransport(), "srv");

        ToolExecutor.ToolResult result = tool.execute("{}");

        assertThat(result.isError()).isTrue();
        assertThat(result.output()).contains("connection lost");
    }

    @Test
    @DisplayName("execute with null result returns empty success")
    void execute_nullResult_returnsEmptySuccess() {
        McpTransport transport = new McpTransport() {
            @Override
            public JsonRpcResponse send(com.agentforge.mcp.JsonRpcRequest request) throws McpException {
                return JsonRpcResponse.parse("{\"jsonrpc\":\"2.0\",\"id\":\"1\",\"result\":null}");
            }
            @Override public boolean isConnected() { return true; }
            @Override public void connect() {}
            @Override public void close() {}
        };
        ToolSchema schema = ToolSchema.noParams("tool", "desc");
        McpTool tool = new McpTool(schema, transport, "srv");

        ToolExecutor.ToolResult result = tool.execute("{}");

        assertThat(result.isError()).isFalse();
        assertThat(result.output()).isEmpty();
    }

    @Test
    @DisplayName("toSchema returns schema with normalized name")
    void toSchema_returnsSchemaWithNormalizedName() {
        ToolSchema schema = ToolSchema.noParams("my_tool", "does something");
        McpTool tool = new McpTool(schema, successTransport("{}"), "server");

        var toolSchema = tool.toSchema();
        assertThat(toolSchema.name()).isEqualTo("mcp__server__my_tool");
        assertThat(toolSchema.description()).isEqualTo("does something");
    }
}
