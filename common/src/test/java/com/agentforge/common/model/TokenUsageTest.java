package com.agentforge.common.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TokenUsageTest {

    @Test
    void zero_allFieldsZero() {
        TokenUsage z = TokenUsage.ZERO;
        assertThat(z.inputTokens()).isZero();
        assertThat(z.outputTokens()).isZero();
        assertThat(z.cacheReadTokens()).isZero();
        assertThat(z.cacheWriteTokens()).isZero();
    }

    @Test
    void totalTokens_sumOfInputAndOutput() {
        TokenUsage usage = new TokenUsage(100, 50, 0, 0);
        assertThat(usage.totalTokens()).isEqualTo(150);
    }

    @Test
    void totalTokens_excludesCacheTokens() {
        TokenUsage usage = new TokenUsage(100, 50, 200, 300);
        assertThat(usage.totalTokens()).isEqualTo(150);
    }

    @Test
    void add_sumsAllFields() {
        TokenUsage a = new TokenUsage(10, 20, 30, 40);
        TokenUsage b = new TokenUsage(1, 2, 3, 4);
        TokenUsage result = a.add(b);
        assertThat(result.inputTokens()).isEqualTo(11);
        assertThat(result.outputTokens()).isEqualTo(22);
        assertThat(result.cacheReadTokens()).isEqualTo(33);
        assertThat(result.cacheWriteTokens()).isEqualTo(44);
    }

    @Test
    void add_withZero_returnsSameValues() {
        TokenUsage usage = new TokenUsage(5, 10, 15, 20);
        TokenUsage result = usage.add(TokenUsage.ZERO);
        assertThat(result).isEqualTo(usage);
    }

    @Test
    void add_isCommutative() {
        TokenUsage a = new TokenUsage(10, 20, 0, 0);
        TokenUsage b = new TokenUsage(5, 8, 0, 0);
        assertThat(a.add(b)).isEqualTo(b.add(a));
    }

    @Test
    void of_factory_setsCacheTokensToZero() {
        TokenUsage usage = TokenUsage.of(100, 50);
        assertThat(usage.inputTokens()).isEqualTo(100);
        assertThat(usage.outputTokens()).isEqualTo(50);
        assertThat(usage.cacheReadTokens()).isZero();
        assertThat(usage.cacheWriteTokens()).isZero();
    }

    @Test
    void negativeInputTokens_throws() {
        assertThatThrownBy(() -> new TokenUsage(-1, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeOutputTokens_throws() {
        assertThatThrownBy(() -> new TokenUsage(0, -1, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"10,20,30", "0,0,0", "100,200,300"})
    void totalTokens_parameterized(int input, int output, int expected) {
        TokenUsage usage = new TokenUsage(input, output, 0, 0);
        assertThat(usage.totalTokens()).isEqualTo(expected);
    }
}
