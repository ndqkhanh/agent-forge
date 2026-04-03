package com.agentforge.common.model;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class ModelInfoTest {

    private static final ModelInfo CLAUDE = new ModelInfo(
            "claude-3-5-sonnet",
            "Claude 3.5 Sonnet",
            "anthropic",
            200_000,
            3.0,
            15.0
    );

    @Test
    void estimateCost_onlyInputTokens() {
        TokenUsage usage = TokenUsage.of(1000, 0);
        double cost = CLAUDE.estimateCost(usage);
        // 1000 * 3.0 / 1000 = 3.0
        assertThat(cost).isCloseTo(3.0, within(0.0001));
    }

    @Test
    void estimateCost_onlyOutputTokens() {
        TokenUsage usage = TokenUsage.of(0, 1000);
        double cost = CLAUDE.estimateCost(usage);
        // 1000 * 15.0 / 1000 = 15.0
        assertThat(cost).isCloseTo(15.0, within(0.0001));
    }

    @Test
    void estimateCost_bothTokenTypes() {
        TokenUsage usage = TokenUsage.of(1000, 500);
        double cost = CLAUDE.estimateCost(usage);
        // 1000 * 3.0/1000 + 500 * 15.0/1000 = 3.0 + 7.5 = 10.5
        assertThat(cost).isCloseTo(10.5, within(0.0001));
    }

    @Test
    void estimateCost_zeroUsage() {
        double cost = CLAUDE.estimateCost(TokenUsage.ZERO);
        assertThat(cost).isZero();
    }

    @Test
    void fields_areStored() {
        assertThat(CLAUDE.id()).isEqualTo("claude-3-5-sonnet");
        assertThat(CLAUDE.displayName()).isEqualTo("Claude 3.5 Sonnet");
        assertThat(CLAUDE.provider()).isEqualTo("anthropic");
        assertThat(CLAUDE.contextWindow()).isEqualTo(200_000);
    }

    @Test
    void blankId_throws() {
        assertThatThrownBy(() -> new ModelInfo("", "Name", "provider", 1000, 1.0, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeContextWindow_throws() {
        assertThatThrownBy(() -> new ModelInfo("id", "Name", "provider", -1, 1.0, 1.0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
