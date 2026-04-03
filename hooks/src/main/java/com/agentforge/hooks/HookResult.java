package com.agentforge.hooks;

public record HookResult(
        HookOutcome outcome,
        String modifiedInput,
        String modifiedOutput,
        String message) {

    public enum HookOutcome {
        ALLOW,
        DENY,
        ERROR
    }

    public static HookResult allow() {
        return new HookResult(HookOutcome.ALLOW, null, null, null);
    }

    public static HookResult allowWithModifiedInput(String input) {
        return new HookResult(HookOutcome.ALLOW, input, null, null);
    }

    public static HookResult allowWithModifiedOutput(String output) {
        return new HookResult(HookOutcome.ALLOW, null, output, null);
    }

    public static HookResult deny(String reason) {
        return new HookResult(HookOutcome.DENY, null, null, reason);
    }

    public static HookResult error(String message) {
        return new HookResult(HookOutcome.ERROR, null, null, message);
    }

    /** Returns a new HookResult with the given input, keeping outcome/output/message. */
    public HookResult withInput(String input) {
        return new HookResult(this.outcome, input, this.modifiedOutput, this.message);
    }
}
