package com.agentforge.mcp.transport;

import com.agentforge.common.error.McpException;
import com.agentforge.mcp.JsonRpcRequest;
import com.agentforge.mcp.JsonRpcResponse;

/**
 * Transport abstraction for JSON-RPC 2.0 over MCP.
 */
public interface McpTransport extends AutoCloseable {

    /**
     * Send a JSON-RPC request and return the response.
     *
     * @param request the request to send
     * @return parsed response
     * @throws McpException on transport or protocol error
     */
    JsonRpcResponse send(JsonRpcRequest request) throws McpException;

    /**
     * Returns true if the transport is currently connected.
     */
    boolean isConnected();

    /**
     * Establish the connection.
     *
     * @throws McpException if connection fails
     */
    void connect() throws McpException;

    @Override
    void close();
}
