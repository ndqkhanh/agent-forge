package com.agentforge.common.event;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Sealed event hierarchy for the Kafka event backbone.
 */
public sealed interface ForgeEvent {

    String eventId();
    String workflowId();
    Instant timestamp();

    record TaskSubmitted(
        String eventId,
        String workflowId,
        String taskId,
        String agentType,
        Map<String, Object> input,
        Instant timestamp
    ) implements ForgeEvent {
        public TaskSubmitted {
            Objects.requireNonNull(eventId);
            Objects.requireNonNull(workflowId);
            Objects.requireNonNull(taskId);
            Objects.requireNonNull(agentType);
            input = input != null ? Map.copyOf(input) : Map.of();
            timestamp = timestamp != null ? timestamp : Instant.now();
        }
    }

    record TaskCompleted(
        String eventId,
        String workflowId,
        String taskId,
        String output,
        long elapsedMs,
        Instant timestamp
    ) implements ForgeEvent {
        public TaskCompleted {
            Objects.requireNonNull(eventId);
            Objects.requireNonNull(workflowId);
            Objects.requireNonNull(taskId);
            timestamp = timestamp != null ? timestamp : Instant.now();
        }
    }

    record SpeculationCheckpoint(
        String eventId,
        String workflowId,
        String nodeId,
        String predictedInput,
        double confidence,
        Instant timestamp
    ) implements ForgeEvent {
        public SpeculationCheckpoint {
            Objects.requireNonNull(eventId);
            Objects.requireNonNull(workflowId);
            Objects.requireNonNull(nodeId);
            timestamp = timestamp != null ? timestamp : Instant.now();
        }
    }

    record RollbackTriggered(
        String eventId,
        String workflowId,
        String nodeId,
        String reason,
        Instant timestamp
    ) implements ForgeEvent {
        public RollbackTriggered {
            Objects.requireNonNull(eventId);
            Objects.requireNonNull(workflowId);
            Objects.requireNonNull(nodeId);
            timestamp = timestamp != null ? timestamp : Instant.now();
        }
    }

    record WorkflowCompleted(
        String eventId,
        String workflowId,
        boolean success,
        long totalElapsedMs,
        double speculationHitRate,
        Instant timestamp
    ) implements ForgeEvent {
        public WorkflowCompleted {
            Objects.requireNonNull(eventId);
            Objects.requireNonNull(workflowId);
            timestamp = timestamp != null ? timestamp : Instant.now();
        }
    }
}
