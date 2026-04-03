package com.agentforge.common.model;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Result of an agent task execution.
 */
public record TaskResult(
    String taskId,
    TaskStatus status,
    String output,
    Map<String, Object> metadata,
    Duration elapsed,
    Instant completedAt
) {
    public TaskResult {
        Objects.requireNonNull(taskId, "taskId must not be null");
        Objects.requireNonNull(status, "status must not be null");
        output = output != null ? output : "";
        metadata = metadata != null ? Map.copyOf(metadata) : Map.of();
        completedAt = completedAt != null ? completedAt : Instant.now();
    }

    public static TaskResult success(String taskId, String output, Duration elapsed) {
        return new TaskResult(taskId, TaskStatus.COMPLETED, output, Map.of(), elapsed, Instant.now());
    }

    public static TaskResult failure(String taskId, String error, Duration elapsed) {
        return new TaskResult(taskId, TaskStatus.FAILED, error, Map.of(), elapsed, Instant.now());
    }

    public boolean isSuccess() {
        return status == TaskStatus.COMPLETED;
    }
}
