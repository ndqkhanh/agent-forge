package com.agentforge.common.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;

class TaskResultTest {

    @Test
    @DisplayName("Success factory creates completed result")
    void successFactory() {
        var result = TaskResult.success("task_1", "output", Duration.ofMillis(500));
        assertThat(result.isSuccess()).isTrue();
        assertThat(result.status()).isEqualTo(TaskStatus.COMPLETED);
        assertThat(result.output()).isEqualTo("output");
        assertThat(result.elapsed()).isEqualTo(Duration.ofMillis(500));
    }

    @Test
    @DisplayName("Failure factory creates failed result")
    void failureFactory() {
        var result = TaskResult.failure("task_1", "error msg", Duration.ofMillis(100));
        assertThat(result.isSuccess()).isFalse();
        assertThat(result.status()).isEqualTo(TaskStatus.FAILED);
        assertThat(result.output()).isEqualTo("error msg");
    }

    @Test
    @DisplayName("Null output defaults to empty string")
    void nullOutputDefaults() {
        var result = new TaskResult("task_1", TaskStatus.COMPLETED, null, null, null, null);
        assertThat(result.output()).isEmpty();
        assertThat(result.metadata()).isEmpty();
        assertThat(result.completedAt()).isNotNull();
    }

    @Test
    @DisplayName("Metadata is immutable")
    void metadataImmutable() {
        var result = TaskResult.success("task_1", "out", Duration.ZERO);
        assertThatThrownBy(() -> result.metadata().put("k", "v"))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
