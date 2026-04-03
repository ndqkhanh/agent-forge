package com.agentforge.common.event;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.*;

class ForgeEventTest {

    @Test
    @DisplayName("TaskSubmitted creates event with immutable input")
    void taskSubmitted() {
        var evt = new ForgeEvent.TaskSubmitted("evt_1", "wf_1", "task_1", "research",
            Map.of("topic", "AI"), Instant.now());
        assertThat(evt.eventId()).isEqualTo("evt_1");
        assertThat(evt.agentType()).isEqualTo("research");
        assertThat(evt.input()).containsEntry("topic", "AI");
        assertThatThrownBy(() -> evt.input().put("k", "v"))
            .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("TaskCompleted stores output and elapsed time")
    void taskCompleted() {
        var evt = new ForgeEvent.TaskCompleted("evt_2", "wf_1", "task_1", "result", 500L, null);
        assertThat(evt.output()).isEqualTo("result");
        assertThat(evt.elapsedMs()).isEqualTo(500L);
        assertThat(evt.timestamp()).isNotNull();
    }

    @Test
    @DisplayName("SpeculationCheckpoint stores confidence")
    void speculationCheckpoint() {
        var evt = new ForgeEvent.SpeculationCheckpoint("evt_3", "wf_1", "node_1", "predicted", 0.87, null);
        assertThat(evt.confidence()).isEqualTo(0.87);
        assertThat(evt.predictedInput()).isEqualTo("predicted");
    }

    @Test
    @DisplayName("RollbackTriggered stores reason")
    void rollbackTriggered() {
        var evt = new ForgeEvent.RollbackTriggered("evt_4", "wf_1", "node_1", "mismatch", null);
        assertThat(evt.reason()).isEqualTo("mismatch");
    }

    @Test
    @DisplayName("WorkflowCompleted stores success and hit rate")
    void workflowCompleted() {
        var evt = new ForgeEvent.WorkflowCompleted("evt_5", "wf_1", true, 3000L, 0.85, null);
        assertThat(evt.success()).isTrue();
        assertThat(evt.speculationHitRate()).isEqualTo(0.85);
    }

    @Test
    @DisplayName("Sealed interface allows exhaustive matching")
    void sealedMatching() {
        ForgeEvent evt = new ForgeEvent.TaskCompleted("e", "w", "t", "out", 100, null);
        String type = switch (evt) {
            case ForgeEvent.TaskSubmitted s -> "submitted";
            case ForgeEvent.TaskCompleted c -> "completed";
            case ForgeEvent.SpeculationCheckpoint sc -> "checkpoint";
            case ForgeEvent.RollbackTriggered r -> "rollback";
            case ForgeEvent.WorkflowCompleted wc -> "workflow_done";
        };
        assertThat(type).isEqualTo("completed");
    }
}
