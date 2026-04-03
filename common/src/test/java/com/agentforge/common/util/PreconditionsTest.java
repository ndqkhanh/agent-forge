package com.agentforge.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

class PreconditionsTest {

    @Test
    @DisplayName("requireNonNull passes for non-null value")
    void requireNonNullPasses() {
        assertThat(Preconditions.requireNonNull("value", "name")).isEqualTo("value");
    }

    @Test
    @DisplayName("requireNonNull throws for null value")
    void requireNonNullThrows() {
        assertThatThrownBy(() -> Preconditions.requireNonNull(null, "field"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("field");
    }

    @Test
    @DisplayName("requireNonBlank passes for non-blank string")
    void requireNonBlankPasses() {
        assertThat(Preconditions.requireNonBlank("hello", "name")).isEqualTo("hello");
    }

    @Test
    @DisplayName("requireNonBlank throws for blank string")
    void requireNonBlankThrowsBlank() {
        assertThatThrownBy(() -> Preconditions.requireNonBlank("  ", "field"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("requireNonBlank throws for null string")
    void requireNonBlankThrowsNull() {
        assertThatThrownBy(() -> Preconditions.requireNonBlank(null, "field"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("requirePositive int passes for positive value")
    void requirePositiveIntPasses() {
        assertThat(Preconditions.requirePositive(5, "count")).isEqualTo(5);
    }

    @Test
    @DisplayName("requirePositive int throws for zero")
    void requirePositiveIntThrowsZero() {
        assertThatThrownBy(() -> Preconditions.requirePositive(0, "count"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("requirePositive int throws for negative")
    void requirePositiveIntThrowsNeg() {
        assertThatThrownBy(() -> Preconditions.requirePositive(-1, "count"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("requireInRange passes for value in range")
    void requireInRangePasses() {
        assertThat(Preconditions.requireInRange(0.5, 0.0, 1.0, "threshold")).isEqualTo(0.5);
    }

    @Test
    @DisplayName("requireInRange throws for value below range")
    void requireInRangeThrowsBelow() {
        assertThatThrownBy(() -> Preconditions.requireInRange(-0.1, 0.0, 1.0, "threshold"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("requireInRange throws for value above range")
    void requireInRangeThrowsAbove() {
        assertThatThrownBy(() -> Preconditions.requireInRange(1.1, 0.0, 1.0, "threshold"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("requireTrue passes for true condition")
    void requireTruePasses() {
        assertThatNoException().isThrownBy(() -> Preconditions.requireTrue(true, "ok"));
    }

    @Test
    @DisplayName("requireTrue throws for false condition")
    void requireTrueThrows() {
        assertThatThrownBy(() -> Preconditions.requireTrue(false, "not ok"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("not ok");
    }
}
