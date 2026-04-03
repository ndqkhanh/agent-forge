package com.agentforge.runtime;

/**
 * Abstraction for pre/post tool-use hook processing.
 * Will be replaced by the actual hooks module interface.
 */
public interface HookPipeline {
    HookResult preToolUse(String toolName, String inputJson);
    String postToolUse(String toolName, String result);

    record HookResult(boolean allowed, String reason, String modifiedInput) {
        public static HookResult allow(String input) {
            return new HookResult(true, null, input);
        }

        public static HookResult deny(String reason) {
            return new HookResult(false, reason, null);
        }
    }
}
