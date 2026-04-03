package com.agentforge.mcp;

import com.agentforge.common.json.JsonParseException;
import com.agentforge.common.json.JsonParser;
import com.agentforge.common.json.JsonValue;
import com.agentforge.common.error.McpException;

/**
 * Immutable JSON-RPC 2.0 response.
 */
public record JsonRpcResponse(String id, JsonValue result, JsonRpcError error) {

    public boolean isError() {
        return error != null;
    }

    public static JsonRpcResponse parse(String json) throws McpException {
        if (json == null || json.isBlank()) {
            throw new McpException("Cannot parse null or empty JSON-RPC response");
        }
        JsonValue parsed;
        try {
            parsed = JsonParser.parse(json);
        } catch (JsonParseException e) {
            throw new McpException("Invalid JSON in JSON-RPC response: " + e.getMessage(), e);
        }
        if (!(parsed instanceof JsonValue.JsonObject obj)) {
            throw new McpException("JSON-RPC response must be a JSON object");
        }

        String id = obj.getString("id").orElse(null);
        JsonValue result = obj.fields().get("result");
        JsonRpcError error = null;

        JsonValue errorVal = obj.fields().get("error");
        if (errorVal instanceof JsonValue.JsonObject errorObj) {
            int code = errorObj.getNumber("code")
                    .map(Double::intValue)
                    .orElse(0);
            String message = errorObj.getString("message").orElse("");
            JsonValue data = errorObj.fields().get("data");
            error = new JsonRpcError(code, message, data);
        }

        return new JsonRpcResponse(id, result, error);
    }

    /**
     * JSON-RPC 2.0 error object.
     */
    public record JsonRpcError(int code, String message, JsonValue data) {}
}
