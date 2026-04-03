package com.agentforge.common.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;

class TaskStatusTest {

    @Test
    @DisplayName("Terminal states are COMPLETED, FAILED, CANCELLED, ROLLED_BACK")
    void terminalStates() {
        assertThat(TaskStatus.COMPLETED.isTerminal()).isTrue();
        assertThat(TaskStatus.FAILED.isTerminal()).isTrue();
        assertThat(TaskStatus.CANCELLED.isTerminal()).isTrue();
        assertThat(TaskStatus.ROLLED_BACK.isTerminal()).isTrue();
    }

    @Test
    @DisplayName("Active states are RUNNING and SPECULATIVE")
    void activeStates() {
        assertThat(TaskStatus.RUNNING.isActive()).isTrue();
        assertThat(TaskStatus.SPECULATIVE.isActive()).isTrue();
    }

    @Test
    @DisplayName("PENDING is neither terminal nor active")
    void pendingState() {
        assertThat(TaskStatus.PENDING.isTerminal()).isFalse();
        assertThat(TaskStatus.PENDING.isActive()).isFalse();
    }

    @ParameterizedTest
    @EnumSource(TaskStatus.class)
    @DisplayName("Every status is either terminal, active, or pending")
    void exhaustiveCheck(TaskStatus status) {
        boolean classified = status.isTerminal() || status.isActive() || status == TaskStatus.PENDING;
        assertThat(classified).isTrue();
    }
}
