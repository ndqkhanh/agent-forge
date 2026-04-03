package com.agentforge.common.error;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AgentForgeExceptionTest {

    @Test
    void apiException_message() {
        ApiException ex = new ApiException("api error");
        assertThat(ex.getMessage()).isEqualTo("api error");
    }

    @Test
    void apiException_withStatusCode() {
        ApiException ex = new ApiException("rate limited", 429, "rate_limit_error");
        assertThat(ex.getStatusCode()).isEqualTo(429);
        assertThat(ex.getErrorType()).isEqualTo("rate_limit_error");
    }

    @Test
    void apiException_withCause() {
        Throwable cause = new RuntimeException("root");
        ApiException ex = new ApiException("wrapped", cause);
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void apiException_isAgentForgeException() {
        ApiException ex = new ApiException("err");
        assertThat(ex).isInstanceOf(AgentForgeException.class);
    }

    @Test
    void toolException_message() {
        ToolException ex = new ToolException("tool failed");
        assertThat(ex.getMessage()).isEqualTo("tool failed");
        assertThat(ex).isInstanceOf(AgentForgeException.class);
    }

    @Test
    void toolException_withCause() {
        Throwable cause = new IllegalStateException("state");
        ToolException ex = new ToolException("tool error", cause);
        assertThat(ex.getCause()).isSameAs(cause);
    }

    @Test
    void sessionException_message() {
        SessionException ex = new SessionException("session not found");
        assertThat(ex.getMessage()).isEqualTo("session not found");
        assertThat(ex).isInstanceOf(AgentForgeException.class);
    }

    @Test
    void configException_message() {
        ConfigException ex = new ConfigException("missing config");
        assertThat(ex.getMessage()).isEqualTo("missing config");
        assertThat(ex).isInstanceOf(AgentForgeException.class);
    }

    @Test
    void hookException_message() {
        HookException ex = new HookException("hook failed");
        assertThat(ex.getMessage()).isEqualTo("hook failed");
        assertThat(ex).isInstanceOf(AgentForgeException.class);
    }

    @Test
    void mcpException_message() {
        McpException ex = new McpException("mcp error");
        assertThat(ex.getMessage()).isEqualTo("mcp error");
        assertThat(ex).isInstanceOf(AgentForgeException.class);
    }

    @Test
    void agentForgeException_isThrownAsRuntimeException() {
        assertThatThrownBy(() -> { throw new ToolException("oops"); })
                .isInstanceOf(RuntimeException.class)
                .hasMessage("oops");
    }

    @Test
    void apiException_toString_containsStatusCode() {
        ApiException ex = new ApiException("err", 500, "server_error");
        assertThat(ex.toString()).contains("500");
    }
}
