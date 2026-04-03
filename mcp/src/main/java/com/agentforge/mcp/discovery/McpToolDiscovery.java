package com.agentforge.mcp.discovery;

import com.agentforge.common.error.McpException;
import com.agentforge.common.json.JsonValue;
import com.agentforge.common.json.JsonWriter;
import com.agentforge.common.model.ToolSchema;
import com.agentforge.mcp.JsonRpcRequest;
import com.agentforge.mcp.JsonRpcResponse;
import com.agentforge.mcp.transport.McpTransport;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Discovers tools from an MCP server via the tools/list JSON-RPC method.
 */
public final class McpToolDiscovery {

    private final McpTransport transport;

    public McpToolDiscovery(McpTransport transport) {
        this.transport = transport;
    }

    /**
     * Call tools/list on the MCP server and convert results to ToolSchema list.
     * MCP response format:
     * { "tools": [ { "name": "...", "description": "...", "inputSchema": {...} }, ... ] }
     */
    public List<ToolSchema> discoverTools() throws McpException {
        JsonRpcRequest request = JsonRpcRequest.create(
                "tools/list",
                new JsonValue.JsonObject(Map.of()));

        JsonRpcResponse response = transport.send(request);

        if (response.isError()) {
            throw new McpException("tools/list failed: " + response.error().message());
        }

        JsonValue result = response.result();
        if (!(result instanceof JsonValue.JsonObject resultObj)) {
            throw new McpException("tools/list response result must be a JSON object");
        }

        List<ToolSchema> schemas = new ArrayList<>();
        resultObj.getArray("tools").ifPresent(toolsArray -> {
            for (JsonValue elem : toolsArray.elements()) {
                if (!(elem instanceof JsonValue.JsonObject toolObj)) {
                    continue;
                }
                String name = toolObj.getString("name").orElse("");
                String description = toolObj.getString("description").orElse("");
                String inputSchemaJson = extractInputSchema(toolObj);
                if (!name.isBlank()) {
                    schemas.add(new ToolSchema(name, description, inputSchemaJson));
                }
            }
        });

        return List.copyOf(schemas);
    }

    private String extractInputSchema(JsonValue.JsonObject toolObj) {
        JsonValue inputSchema = toolObj.fields().get("inputSchema");
        if (inputSchema != null) {
            return JsonWriter.write(inputSchema);
        }
        return "{\"type\":\"object\",\"properties\":{}}";
    }
}
