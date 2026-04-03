package com.agentforge.runtime;

/**
 * Abstraction for executing tools by name with JSON input.
 * Will be replaced by the actual tools module interface.
 */
public interface ToolExecutor {
    String execute(String toolName, String inputJson);
    boolean supports(String toolName);
}
