package com.agentforge.mcp;

import com.agentforge.common.json.JsonParser;
import com.agentforge.common.json.JsonValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("JsonRpcRequest")
class JsonRpcRequestTest {

    @Test
    @DisplayName("create sets method and params")
    void create_setsMethodAndParams() {
        JsonValue params = new JsonValue.JsonObject(Map.of("key", new JsonValue.JsonString("val")));
        JsonRpcRequest req = JsonRpcRequest.create("tools/list", params);

        assertThat(req.method()).isEqualTo("tools/list");
        assertThat(req.params()).isEqualTo(params);
    }

    @Test
    @DisplayName("create auto-generates a non-null UUID id")
    void create_autoGeneratesUuidId() {
        JsonRpcRequest req = JsonRpcRequest.create("ping", new JsonValue.JsonNull());

        assertThat(req.id()).isNotNull();
        assertThat(req.id()).isNotBlank();
        // UUID format: 8-4-4-4-12 hex digits
        assertThat(req.id()).matches(
                "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
    }

    @Test
    @DisplayName("two created requests have different ids")
    void create_twoRequests_haveDifferentIds() {
        JsonRpcRequest r1 = JsonRpcRequest.create("method", new JsonValue.JsonNull());
        JsonRpcRequest r2 = JsonRpcRequest.create("method", new JsonValue.JsonNull());

        assertThat(r1.id()).isNotEqualTo(r2.id());
    }

    @Test
    @DisplayName("toJson includes jsonrpc 2.0 field")
    void toJson_includesJsonrpc20() {
        JsonRpcRequest req = JsonRpcRequest.create("ping", new JsonValue.JsonNull());
        String json = req.toJson();

        JsonValue parsed = JsonParser.parse(json);
        assertThat(parsed).isInstanceOf(JsonValue.JsonObject.class);
        JsonValue.JsonObject obj = (JsonValue.JsonObject) parsed;
        assertThat(obj.getString("jsonrpc")).hasValue("2.0");
    }

    @Test
    @DisplayName("toJson includes id, method, and params")
    void toJson_includesIdMethodParams() {
        JsonValue params = new JsonValue.JsonObject(
                Map.of("arg", new JsonValue.JsonString("hello")));
        JsonRpcRequest req = new JsonRpcRequest("test-id-1", "tools/call", params);
        String json = req.toJson();

        JsonValue.JsonObject obj = (JsonValue.JsonObject) JsonParser.parse(json);
        assertThat(obj.getString("id")).hasValue("test-id-1");
        assertThat(obj.getString("method")).hasValue("tools/call");
        assertThat(obj.fields()).containsKey("params");
    }

    @Test
    @DisplayName("toJson with null params serializes null")
    void toJson_nullParams_serializesNull() {
        JsonRpcRequest req = new JsonRpcRequest("id-null", "ping", null);
        String json = req.toJson();

        JsonValue.JsonObject obj = (JsonValue.JsonObject) JsonParser.parse(json);
        assertThat(obj.fields().get("params")).isInstanceOf(JsonValue.JsonNull.class);
    }

    @Test
    @DisplayName("toJson with empty object params is valid JSON")
    void toJson_emptyObjectParams_isValidJson() {
        JsonRpcRequest req = JsonRpcRequest.create(
                "tools/list", new JsonValue.JsonObject(Map.of()));
        String json = req.toJson();

        assertThat(json).isNotBlank();
        JsonValue parsed = JsonParser.parse(json);
        assertThat(parsed).isInstanceOf(JsonValue.JsonObject.class);
    }
}
