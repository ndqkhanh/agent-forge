package com.agentforge.hooks;

import com.agentforge.hooks.HookResult.HookOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class HookPipelineTest {

    private final HookRunner runner = new HookRunner(5_000L);

    private HookDefinition pre(String name, String command, String... patterns) {
        return new HookDefinition(name, HookType.PRE_TOOL_USE, command, List.of(patterns));
    }

    private HookDefinition post(String name, String command, String... patterns) {
        return new HookDefinition(name, HookType.POST_TOOL_USE, command, List.of(patterns));
    }

    @Test
    @DisplayName("empty pipeline returns ALLOW")
    void emptyPipeline_returnsAllow() {
        HookResult result = HookPipeline.empty().executePreToolUse("bash", "{}");
        assertThat(result.outcome()).isEqualTo(HookOutcome.ALLOW);
    }

    @Test
    @DisplayName("empty() hookCount is zero")
    void emptyPipeline_hookCountIsZero() {
        assertThat(HookPipeline.empty().hookCount()).isZero();
    }

    @Test
    @DisplayName("single allow hook passes through")
    void singleAllowHook_passesThrough() {
        HookPipeline pipeline = new HookPipeline(List.of(pre("p1", "exit 0", "*")), runner);
        HookResult result = pipeline.executePreToolUse("bash", "{}");
        assertThat(result.outcome()).isEqualTo(HookOutcome.ALLOW);
    }

    @Test
    @DisplayName("single deny hook short-circuits with DENY")
    void singleDenyHook_shortCircuits() {
        HookPipeline pipeline = new HookPipeline(
                List.of(pre("deny", "echo 'blocked'; exit 2", "*")), runner);
        HookResult result = pipeline.executePreToolUse("bash", "{}");
        assertThat(result.outcome()).isEqualTo(HookOutcome.DENY);
        assertThat(result.message()).contains("blocked");
    }

    @Test
    @DisplayName("multiple allow hooks — all run, result is ALLOW")
    void multipleAllowHooks_allRun_returnAllow() {
        HookPipeline pipeline = new HookPipeline(List.of(
                pre("p1", "exit 0", "*"),
                pre("p2", "exit 0", "*")), runner);
        HookResult result = pipeline.executePreToolUse("bash", "{}");
        assertThat(result.outcome()).isEqualTo(HookOutcome.ALLOW);
    }

    @Test
    @DisplayName("second hook denies — short-circuits after first")
    void secondHookDenies_shortCircuitsAfterFirst() {
        HookPipeline pipeline = new HookPipeline(List.of(
                pre("p1", "exit 0", "*"),
                pre("p2", "echo 'denied'; exit 2", "*")), runner);
        HookResult result = pipeline.executePreToolUse("bash", "{}");
        assertThat(result.outcome()).isEqualTo(HookOutcome.DENY);
    }

    @Test
    @DisplayName("input modification chaining — hook2 sees output of hook1")
    void inputModificationChaining_hook2SeesHook1Output() {
        // hook1 outputs modified JSON, hook2 echoes stdin back
        HookPipeline pipeline = new HookPipeline(List.of(
                pre("p1", "echo '{\"modified\":true}'", "*"),
                pre("p2", "cat", "*")), runner);
        HookResult result = pipeline.executePreToolUse("bash", "{}");
        assertThat(result.outcome()).isEqualTo(HookOutcome.ALLOW);
        // Final modifiedInput should have the chained value from hook1
        assertThat(result.modifiedInput()).contains("modified");
    }

    @Test
    @DisplayName("only matching hooks are executed")
    void onlyMatchingHooks_areExecuted() {
        HookPipeline pipeline = new HookPipeline(List.of(
                pre("deny-python", "echo 'denied'; exit 2", "python"),
                pre("allow-bash", "exit 0", "bash")), runner);
        // Running bash should skip the python-deny hook
        HookResult result = pipeline.executePreToolUse("bash", "{}");
        assertThat(result.outcome()).isEqualTo(HookOutcome.ALLOW);
    }

    @Test
    @DisplayName("PostToolUse hooks chain output modifications")
    void postToolUseHooks_chainOutputModifications() {
        HookPipeline pipeline = new HookPipeline(List.of(
                post("fmt", "echo 'formatted'", "*")), runner);
        HookResult result = pipeline.executePostToolUse("file_write", "raw output");
        assertThat(result.outcome()).isEqualTo(HookOutcome.ALLOW);
        assertThat(result.modifiedOutput()).isEqualTo("formatted");
    }

    @Test
    @DisplayName("PostToolUse error short-circuits")
    void postToolUseError_shortCircuits() {
        HookPipeline pipeline = new HookPipeline(List.of(
                post("bad", "exit 1", "*"),
                post("good", "exit 0", "*")), runner);
        HookResult result = pipeline.executePostToolUse("bash", "{}");
        assertThat(result.outcome()).isEqualTo(HookOutcome.ERROR);
    }

    @Test
    @DisplayName("mixed hook types — PRE hooks filtered for executePreToolUse")
    void mixedHookTypes_preFilteredCorrectly() {
        HookPipeline pipeline = new HookPipeline(List.of(
                post("post1", "echo 'denied'; exit 2", "*"),  // should be skipped
                pre("pre1", "exit 0", "*")), runner);
        HookResult result = pipeline.executePreToolUse("bash", "{}");
        assertThat(result.outcome()).isEqualTo(HookOutcome.ALLOW);
    }

    @Test
    @DisplayName("mixed hook types — POST hooks filtered for executePostToolUse")
    void mixedHookTypes_postFilteredCorrectly() {
        HookPipeline pipeline = new HookPipeline(List.of(
                pre("pre1", "echo 'denied'; exit 2", "*"),  // should be skipped
                post("post1", "exit 0", "*")), runner);
        HookResult result = pipeline.executePostToolUse("bash", "{}");
        assertThat(result.outcome()).isEqualTo(HookOutcome.ALLOW);
    }

    @Test
    @DisplayName("hookCount returns correct number of hooks")
    void hookCount_returnsCorrectCount() {
        HookPipeline pipeline = new HookPipeline(List.of(
                pre("p1", "exit 0", "*"),
                pre("p2", "exit 0", "*"),
                post("p3", "exit 0", "*")), runner);
        assertThat(pipeline.hookCount()).isEqualTo(3);
    }
}
