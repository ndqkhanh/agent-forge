package com.agentforge.common.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.HashSet;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

class IdGeneratorTest {

    @Test
    @DisplayName("Generated IDs have correct prefix")
    void correctPrefix() {
        assertThat(IdGenerator.messageId()).startsWith("msg_");
        assertThat(IdGenerator.conversationId()).startsWith("conv_");
        assertThat(IdGenerator.taskId()).startsWith("task_");
        assertThat(IdGenerator.workflowId()).startsWith("wf_");
        assertThat(IdGenerator.eventId()).startsWith("evt_");
        assertThat(IdGenerator.toolCallId()).startsWith("tc_");
        assertThat(IdGenerator.hookId()).startsWith("hook_");
        assertThat(IdGenerator.bufferId()).startsWith("buf_");
    }

    @Test
    @DisplayName("Generated IDs are unique")
    void unique() {
        var ids = new HashSet<String>();
        IntStream.range(0, 1000).forEach(i -> ids.add(IdGenerator.messageId()));
        assertThat(ids).hasSize(1000);
    }

    @Test
    @DisplayName("IDs are sortable (time-ordered prefix)")
    void sortable() throws InterruptedException {
        var id1 = IdGenerator.generate("test");
        Thread.sleep(5);
        var id2 = IdGenerator.generate("test");
        assertThat(id1.compareTo(id2)).isLessThan(0);
    }

    @Test
    @DisplayName("Custom prefix works")
    void customPrefix() {
        var id = IdGenerator.generate("custom");
        assertThat(id).startsWith("custom_");
        assertThat(id.length()).isGreaterThan("custom_".length());
    }
}
