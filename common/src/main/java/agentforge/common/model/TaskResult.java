package agentforge.common.model;

import java.time.Duration;
import java.time.Instant;

/**
 * Result of executing a task.
 */
public record TaskResult(
        TaskId taskId,
        Status status,
        String output,
        String error,
        Duration duration,
        Instant completedAt) {

    public enum Status {
        SUCCESS, FAILED, TIMED_OUT, CANCELLED
    }

    public static TaskResult success(TaskId taskId, String output, Duration duration) {
        return new TaskResult(taskId, Status.SUCCESS, output, null, duration, Instant.now());
    }

    public static TaskResult failed(TaskId taskId, String error, Duration duration) {
        return new TaskResult(taskId, Status.FAILED, null, error, duration, Instant.now());
    }

    public boolean isSuccess() {
        return status == Status.SUCCESS;
    }
}
