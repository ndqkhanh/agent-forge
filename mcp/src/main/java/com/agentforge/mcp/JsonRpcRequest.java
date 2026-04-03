package com.agentforge.mcp;

import com.agentforge.common.json.JsonValue;
import com.agentforge.common.json.JsonWriter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Immutable JSON-RPC 2.0 request.
 */
public record JsonRpcRequest(String id, String method, JsonValue params) {

    public String toJson() {
        Map<String, JsonValue> fields = new LinkedHashMap<>();
        fields.put("jsonrpc", new JsonValue.JsonString("2.0"));
        fields.put("id", new JsonValue.JsonString(id));
        fields.put("method", new JsonValue.JsonString(method));
        if (params != null) {
            fields.put("params", params);
        } else {
            fields.put("params", new JsonValue.JsonNull());
        }
        return JsonWriter.write(new JsonValue.JsonObject(fields));
    }

    public static JsonRpcRequest create(String method, JsonValue params) {
        return new JsonRpcRequest(UUID.randomUUID().toString(), method, params);
    }
}
