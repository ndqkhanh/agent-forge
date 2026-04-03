package com.agentforge.mcp;

import com.agentforge.common.error.McpException;
import com.agentforge.common.json.JsonValue;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("JsonRpcResponse")
class JsonRpcResponseTest {

    @Test
    @DisplayName("parse success response extracts id and result")
    void parse_successResponse_extractsIdAndResult() throws McpException {
        String json = """
                {"jsonrpc":"2.0","id":"req-1","result":{"output":"hello"}}
                """;
        JsonRpcResponse resp = JsonRpcResponse.parse(json);

        assertThat(resp.id()).isEqualTo("req-1");
        assertThat(resp.result()).isInstanceOf(JsonValue.JsonObject.class);
        assertThat(resp.isError()).isFalse();
        assertThat(resp.error()).isNull();
    }

    @Test
    @DisplayName("parse error response extracts error object")
    void parse_errorResponse_extractsError() throws McpException {
        String json = """
                {"jsonrpc":"2.0","id":"req-2","error":{"code":-32601,"message":"Method not found"}}
                """;
        JsonRpcResponse resp = JsonRpcResponse.parse(json);

        assertThat(resp.isError()).isTrue();
        assertThat(resp.error()).isNotNull();
        assertThat(resp.error().code()).isEqualTo(-32601);
        assertThat(resp.error().message()).isEqualTo("Method not found");
    }

    @Test
    @DisplayName("isError returns true when error present")
    void isError_whenErrorPresent_returnsTrue() throws McpException {
        String json = """
                {"jsonrpc":"2.0","id":"x","error":{"code":-1,"message":"fail"}}
                """;
        assertThat(JsonRpcResponse.parse(json).isError()).isTrue();
    }

    @Test
    @DisplayName("isError returns false when no error")
    void isError_whenNoError_returnsFalse() throws McpException {
        String json = """
                {"jsonrpc":"2.0","id":"x","result":{"ok":true}}
                """;
        assertThat(JsonRpcResponse.parse(json).isError()).isFalse();
    }

    @Test
    @DisplayName("parse response with null result field")
    void parse_nullResult_succeeds() throws McpException {
        String json = """
                {"jsonrpc":"2.0","id":"y","result":null}
                """;
        JsonRpcResponse resp = JsonRpcResponse.parse(json);
        assertThat(resp.isError()).isFalse();
        assertThat(resp.result()).isInstanceOf(JsonValue.JsonNull.class);
    }

    @Test
    @DisplayName("parse invalid JSON throws McpException")
    void parse_invalidJson_throwsMcpException() {
        assertThatThrownBy(() -> JsonRpcResponse.parse("not json"))
                .isInstanceOf(McpException.class)
                .hasMessageContaining("Invalid JSON");
    }

    @Test
    @DisplayName("parse null input throws McpException")
    void parse_nullInput_throwsMcpException() {
        assertThatThrownBy(() -> JsonRpcResponse.parse(null))
                .isInstanceOf(McpException.class);
    }

    @Test
    @DisplayName("parse non-object JSON throws McpException")
    void parse_nonObjectJson_throwsMcpException() {
        assertThatThrownBy(() -> JsonRpcResponse.parse("[1,2,3]"))
                .isInstanceOf(McpException.class)
                .hasMessageContaining("JSON object");
    }

    @Test
    @DisplayName("error code and message extracted correctly")
    void parse_errorCodeAndMessage_extractedCorrectly() throws McpException {
        String json = """
                {"jsonrpc":"2.0","id":"e","error":{"code":-32700,"message":"Parse error","data":null}}
                """;
        JsonRpcResponse resp = JsonRpcResponse.parse(json);

        assertThat(resp.error().code()).isEqualTo(-32700);
        assertThat(resp.error().message()).isEqualTo("Parse error");
    }
}
