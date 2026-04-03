package com.agentforge.hooks;

import com.agentforge.hooks.HookResult.HookOutcome;

import java.util.List;

/**
 * Chains multiple hooks with short-circuit semantics.
 *
 * <p>PreToolUse: short-circuits on DENY or ERROR; chains input modifications.
 * <p>PostToolUse: short-circuits on ERROR; chains output modifications.
 */
public final class HookPipeline {

    private final List<HookDefinition> hooks;
    private final HookRunner runner;

    public HookPipeline(List<HookDefinition> hooks, HookRunner runner) {
        this.hooks = List.copyOf(hooks);
        this.runner = runner;
    }

    /**
     * Execute all PreToolUse hooks for a given tool.
     * Short-circuits on first DENY or ERROR.
     * Chains input modifications: output of hook N becomes input of hook N+1.
     */
    public HookResult executePreToolUse(String toolName, String inputJson) {
        String currentInput = inputJson;
        for (HookDefinition hook : hooks) {
            if (hook.type() != HookType.PRE_TOOL_USE) continue;
            if (!hook.matchesTool(toolName)) continue;

            HookResult result = runner.run(hook, toolName, currentInput);
            if (result.outcome() == HookOutcome.DENY || result.outcome() == HookOutcome.ERROR) {
                return result;
            }
            if (result.modifiedInput() != null) {
                currentInput = result.modifiedInput();
            }
        }
        return HookResult.allow().withInput(currentInput);
    }

    /**
     * Execute all PostToolUse hooks for a given tool.
     * Short-circuits on ERROR (DENY also stops the chain).
     * Chains output modifications: output of hook N becomes input of hook N+1.
     */
    public HookResult executePostToolUse(String toolName, String outputJson) {
        String currentOutput = outputJson;
        for (HookDefinition hook : hooks) {
            if (hook.type() != HookType.POST_TOOL_USE) continue;
            if (!hook.matchesTool(toolName)) continue;

            HookResult result = runner.run(hook, toolName, currentOutput);
            if (result.outcome() == HookOutcome.ERROR || result.outcome() == HookOutcome.DENY) {
                return result;
            }
            if (result.modifiedInput() != null) {
                // For post-tool-use, the hook echoes back the modified output via modifiedInput channel
                currentOutput = result.modifiedInput();
            }
        }
        return HookResult.allowWithModifiedOutput(currentOutput);
    }

    public static HookPipeline empty() {
        return new HookPipeline(List.of(), new HookRunner(10_000L));
    }

    public int hookCount() {
        return hooks.size();
    }
}
