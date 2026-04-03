package com.agentforge.hooks;

import com.agentforge.hooks.HookResult.HookOutcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class HookResultTest {

    @Test
    @DisplayName("allow() produces ALLOW outcome with no payload")
    void allow_hasCorrectOutcome() {
        HookResult result = HookResult.allow();
        assertThat(result.outcome()).isEqualTo(HookOutcome.ALLOW);
        assertThat(result.modifiedInput()).isNull();
        assertThat(result.modifiedOutput()).isNull();
        assertThat(result.message()).isNull();
    }

    @Test
    @DisplayName("allowWithModifiedInput() carries modified input")
    void allowWithModifiedInput_setsModifiedInput() {
        HookResult result = HookResult.allowWithModifiedInput("{\"key\":\"value\"}");
        assertThat(result.outcome()).isEqualTo(HookOutcome.ALLOW);
        assertThat(result.modifiedInput()).isEqualTo("{\"key\":\"value\"}");
        assertThat(result.modifiedOutput()).isNull();
    }

    @Test
    @DisplayName("allowWithModifiedOutput() carries modified output")
    void allowWithModifiedOutput_setsModifiedOutput() {
        HookResult result = HookResult.allowWithModifiedOutput("formatted output");
        assertThat(result.outcome()).isEqualTo(HookOutcome.ALLOW);
        assertThat(result.modifiedOutput()).isEqualTo("formatted output");
        assertThat(result.modifiedInput()).isNull();
    }

    @Test
    @DisplayName("deny() produces DENY outcome with reason message")
    void deny_hasCorrectOutcomeAndMessage() {
        HookResult result = HookResult.deny("secret detected");
        assertThat(result.outcome()).isEqualTo(HookOutcome.DENY);
        assertThat(result.message()).isEqualTo("secret detected");
        assertThat(result.modifiedInput()).isNull();
        assertThat(result.modifiedOutput()).isNull();
    }

    @Test
    @DisplayName("error() produces ERROR outcome with message")
    void error_hasCorrectOutcomeAndMessage() {
        HookResult result = HookResult.error("process failed");
        assertThat(result.outcome()).isEqualTo(HookOutcome.ERROR);
        assertThat(result.message()).isEqualTo("process failed");
    }

    @Test
    @DisplayName("HookOutcome has exactly three values")
    void hookOutcome_hasThreeValues() {
        assertThat(HookOutcome.values()).containsExactlyInAnyOrder(
                HookOutcome.ALLOW, HookOutcome.DENY, HookOutcome.ERROR);
    }

    @Test
    @DisplayName("withInput() returns new HookResult with given input")
    void withInput_returnsNewResultWithInput() {
        HookResult base = HookResult.allow();
        HookResult updated = base.withInput("new-input");
        assertThat(updated.modifiedInput()).isEqualTo("new-input");
        assertThat(updated.outcome()).isEqualTo(HookOutcome.ALLOW);
        // original unchanged
        assertThat(base.modifiedInput()).isNull();
    }
}
