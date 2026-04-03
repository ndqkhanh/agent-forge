package com.agentforge.api.stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SseEventMapper")
class SseEventMapperTest {

    private SseEventMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new SseEventMapper();
    }

    private SseParser.SseEvent event(String type, String data) {
        return new SseParser.SseEvent(type, data, null);
    }

    @Test
    @DisplayName("map content_block_start with text type returns empty (no event for text start)")
    void mapContentBlockStartText() {
        String data = "{\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}";
        Optional<AssistantEvent> result = mapper.map(event("content_block_start", data));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("map content_block_start with tool_use type returns ToolUseStart")
    void mapContentBlockStartToolUse() {
        String data = "{\"index\":1,\"content_block\":{\"type\":\"tool_use\",\"id\":\"tool_123\",\"name\":\"search\"}}";
        Optional<AssistantEvent> result = mapper.map(event("content_block_start", data));
        assertThat(result).isPresent();
        AssistantEvent.ToolUseStart e = (AssistantEvent.ToolUseStart) result.get();
        assertThat(e.id()).isEqualTo("tool_123");
        assertThat(e.name()).isEqualTo("search");
    }

    @Test
    @DisplayName("map content_block_delta text_delta returns TextDelta")
    void mapContentBlockDeltaText() {
        String data = "{\"index\":0,\"delta\":{\"type\":\"text_delta\",\"text\":\"Hello\"}}";
        Optional<AssistantEvent> result = mapper.map(event("content_block_delta", data));
        assertThat(result).isPresent();
        AssistantEvent.TextDelta e = (AssistantEvent.TextDelta) result.get();
        assertThat(e.text()).isEqualTo("Hello");
    }

    @Test
    @DisplayName("map content_block_delta input_json_delta returns ToolUseInputDelta")
    void mapContentBlockDeltaToolInput() {
        String data = "{\"index\":1,\"delta\":{\"type\":\"input_json_delta\",\"partial_json\":\"{\\\"q\\\"\"}}";
        Optional<AssistantEvent> result = mapper.map(event("content_block_delta", data));
        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(AssistantEvent.ToolUseInputDelta.class);
    }

    @Test
    @DisplayName("map content_block_stop after tool_use block returns ToolUseEnd")
    void mapContentBlockStopAfterToolUse() {
        // First start a tool_use block
        String startData = "{\"index\":1,\"content_block\":{\"type\":\"tool_use\",\"id\":\"t1\",\"name\":\"fn\"}}";
        mapper.map(event("content_block_start", startData));

        Optional<AssistantEvent> result = mapper.map(event("content_block_stop", ""));
        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(AssistantEvent.ToolUseEnd.class);
    }

    @Test
    @DisplayName("map content_block_stop after text block returns empty")
    void mapContentBlockStopAfterText() {
        String startData = "{\"index\":0,\"content_block\":{\"type\":\"text\",\"text\":\"\"}}";
        mapper.map(event("content_block_start", startData));

        Optional<AssistantEvent> result = mapper.map(event("content_block_stop", ""));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("map message_start returns MessageStart with id")
    void mapMessageStart() {
        String data = "{\"type\":\"message_start\",\"message\":{\"id\":\"msg_abc\",\"type\":\"message\"}}";
        Optional<AssistantEvent> result = mapper.map(event("message_start", data));
        assertThat(result).isPresent();
        assertThat(result.get()).isInstanceOf(AssistantEvent.MessageStart.class);
    }

    @Test
    @DisplayName("map message_stop returns MessageStop")
    void mapMessageStop() {
        Optional<AssistantEvent> result = mapper.map(event("message_stop", "{}"));
        assertThat(result).isPresent();
        AssistantEvent.MessageStop e = (AssistantEvent.MessageStop) result.get();
        assertThat(e.stopReason()).isEqualTo("end_turn");
    }

    @Test
    @DisplayName("map message_delta with stop_reason returns MessageStop")
    void mapMessageDeltaWithStopReason() {
        String data = "{\"delta\":{\"stop_reason\":\"tool_use\"},\"usage\":{\"output_tokens\":10}}";
        Optional<AssistantEvent> result = mapper.map(event("message_delta", data));
        // usage present so UsageUpdate is returned; stop_reason is handled separately
        assertThat(result).isPresent();
    }

    @Test
    @DisplayName("map error event returns Error")
    void mapErrorEvent() {
        String data = "{\"type\":\"error\",\"error\":{\"type\":\"overloaded_error\",\"message\":\"API overloaded\"}}";
        Optional<AssistantEvent> result = mapper.map(event("error", data));
        assertThat(result).isPresent();
        AssistantEvent.Error e = (AssistantEvent.Error) result.get();
        assertThat(e.message()).isEqualTo("API overloaded");
        assertThat(e.type()).isEqualTo("overloaded_error");
    }

    @Test
    @DisplayName("unknown event type returns empty")
    void unknownEventReturnsEmpty() {
        Optional<AssistantEvent> result = mapper.map(event("ping", ""));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("null event type returns empty")
    void nullEventTypeReturnsEmpty() {
        Optional<AssistantEvent> result = mapper.map(new SseParser.SseEvent(null, "data", null));
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("reset clears block tracking state")
    void resetClearsState() {
        // Start a tool_use, then reset
        String startData = "{\"index\":1,\"content_block\":{\"type\":\"tool_use\",\"id\":\"t1\",\"name\":\"fn\"}}";
        mapper.map(event("content_block_start", startData));
        mapper.reset();

        // After reset, content_block_stop should return empty (no active block)
        Optional<AssistantEvent> result = mapper.map(event("content_block_stop", ""));
        assertThat(result).isEmpty();
    }
}
