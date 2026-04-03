package com.agentforge.mcp.discovery;

import com.agentforge.common.error.McpException;
import com.agentforge.common.json.JsonParser;
import com.agentforge.common.json.JsonValue;
import com.agentforge.common.json.JsonWriter;
import com.agentforge.common.model.ToolSchema;
import com.agentforge.mcp.JsonRpcRequest;
import com.agentforge.mcp.JsonRpcResponse;
import com.agentforge.mcp.transport.McpTransport;
import com.agentforge.tools.Tool;
import com.agentforge.tools.ToolExecutor;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Wraps an MCP server tool as a local Tool.
 * Names follow the convention: mcp__{normalized_server}__{normalized_tool}
 */
public final class McpTool implements Tool {

    private final ToolSchema schema;
    private final McpTransport transport;
    private final String serverName;

    public McpTool(ToolSchema schema, McpTransport transport, String serverName) {
        this.schema = schema;
        this.transport = transport;
        this.serverName = serverName;
    }

    @Override
    public String name() {
        return "mcp__" + normalizeServer(serverName) + "__" + normalizeTool(schema.name());
    }

    @Override
    public String description() {
        return schema.description();
    }

    @Override
    public String inputSchema() {
        return schema.inputSchemaJson();
    }

    @Override
    public ToolExecutor.ToolResult execute(String inputJson) {
        try {
            JsonValue arguments;
            if (inputJson == null || inputJson.isBlank()) {
                arguments = new JsonValue.JsonObject(Map.of());
            } else {
                arguments = JsonParser.parse(inputJson);
            }

            Map<String, JsonValue> paramsFields = new LinkedHashMap<>();
            paramsFields.put("name", new JsonValue.JsonString(schema.name()));
            paramsFields.put("arguments", arguments);
            JsonValue params = new JsonValue.JsonObject(paramsFields);

            JsonRpcRequest request = JsonRpcRequest.create("tools/call", params);
            JsonRpcResponse response = transport.send(request);

            if (response.isError()) {
                return ToolExecutor.ToolResult.error(
                        "MCP error: " + response.error().message());
            }

            JsonValue result = response.result();
            if (result == null || result instanceof JsonValue.JsonNull) {
                return ToolExecutor.ToolResult.success("");
            }
            return ToolExecutor.ToolResult.success(JsonWriter.write(result));
        } catch (McpException e) {
            return ToolExecutor.ToolResult.error("MCP transport error: " + e.getMessage());
        } catch (Exception e) {
            return ToolExecutor.ToolResult.error("Unexpected error: " + e.getMessage());
        }
    }

    private static String normalizeServer(String name) {
        return name.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
    }

    private static String normalizeTool(String name) {
        return name.replaceAll("[^a-zA-Z0-9]", "_").toLowerCase();
    }
}
