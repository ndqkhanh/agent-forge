package com.agentforge.api.stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("SseParser")
class SseParserTest {

    private SseParser parser;

    @BeforeEach
    void setUp() {
        parser = new SseParser();
    }

    @Test
    @DisplayName("parse single complete event")
    void parseSingleCompleteEvent() {
        List<SseParser.SseEvent> events = parser.feed("event: message_start\ndata: {\"id\":\"msg_1\"}\n\n");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).event()).isEqualTo("message_start");
        assertThat(events.get(0).data()).isEqualTo("{\"id\":\"msg_1\"}");
    }

    @Test
    @DisplayName("parse event with multi-line data")
    void parseMultiLineData() {
        String chunk = "event: test\ndata: line1\ndata: line2\ndata: line3\n\n";
        List<SseParser.SseEvent> events = parser.feed(chunk);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).data()).isEqualTo("line1\nline2\nline3");
    }

    @Test
    @DisplayName("parse multiple events from single chunk")
    void parseMultipleEventsFromSingleChunk() {
        String chunk = "event: a\ndata: first\n\nevent: b\ndata: second\n\n";
        List<SseParser.SseEvent> events = parser.feed(chunk);
        assertThat(events).hasSize(2);
        assertThat(events.get(0).event()).isEqualTo("a");
        assertThat(events.get(0).data()).isEqualTo("first");
        assertThat(events.get(1).event()).isEqualTo("b");
        assertThat(events.get(1).data()).isEqualTo("second");
    }

    @Test
    @DisplayName("handle partial chunks — event split across two feeds")
    void handlePartialChunksEventSplitAcrossFeeds() {
        List<SseParser.SseEvent> first = parser.feed("event: msg\ndata: hel");
        assertThat(first).isEmpty();

        List<SseParser.SseEvent> second = parser.feed("lo\n\n");
        assertThat(second).hasSize(1);
        assertThat(second.get(0).data()).isEqualTo("hello");
    }

    @Test
    @DisplayName("handle chunk boundary in the middle of a line")
    void handleChunkBoundaryMiddleOfLine() {
        List<SseParser.SseEvent> first = parser.feed("event: te");
        assertThat(first).isEmpty();

        List<SseParser.SseEvent> second = parser.feed("st\ndata: ok\n\n");
        assertThat(second).hasSize(1);
        assertThat(second.get(0).event()).isEqualTo("test");
        assertThat(second.get(0).data()).isEqualTo("ok");
    }

    @Test
    @DisplayName("handle chunk boundary in the middle of a field name")
    void handleChunkBoundaryMiddleOfFieldName() {
        List<SseParser.SseEvent> first = parser.feed("dat");
        assertThat(first).isEmpty();

        List<SseParser.SseEvent> second = parser.feed("a: payload\n\n");
        assertThat(second).hasSize(1);
        assertThat(second.get(0).data()).isEqualTo("payload");
    }

    @Test
    @DisplayName("parse comment lines and ignore them")
    void parseCommentLines() {
        String chunk = ": this is a comment\ndata: real\n\n";
        List<SseParser.SseEvent> events = parser.feed(chunk);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).data()).isEqualTo("real");
    }

    @Test
    @DisplayName("handle empty data field")
    void handleEmptyDataField() {
        List<SseParser.SseEvent> events = parser.feed("event: ping\ndata: \n\n");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).data()).isEmpty();
    }

    @Test
    @DisplayName("handle event with id field")
    void handleEventWithIdField() {
        List<SseParser.SseEvent> events = parser.feed("id: 42\ndata: hello\n\n");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).id()).isEqualTo("42");
    }

    @Test
    @DisplayName("handle event without event field (null type)")
    void handleEventWithoutEventField() {
        List<SseParser.SseEvent> events = parser.feed("data: bare\n\n");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).event()).isNull();
        assertThat(events.get(0).data()).isEqualTo("bare");
    }

    @Test
    @DisplayName("handle consecutive empty lines without data (no event dispatched)")
    void handleConsecutiveEmptyLinesWithoutData() {
        List<SseParser.SseEvent> events = parser.feed("\n\n\n");
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("handle Windows-style line endings CRLF")
    void handleCrlfLineEndings() {
        List<SseParser.SseEvent> events = parser.feed("event: test\r\ndata: crlf\r\n\r\n");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).event()).isEqualTo("test");
        assertThat(events.get(0).data()).isEqualTo("crlf");
    }

    @Test
    @DisplayName("handle mixed LF and CRLF line endings")
    void handleMixedLineEndings() {
        List<SseParser.SseEvent> events = parser.feed("event: mix\r\ndata: value\n\n");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).event()).isEqualTo("mix");
        assertThat(events.get(0).data()).isEqualTo("value");
    }

    @Test
    @DisplayName("reset clears buffer and accumulated state")
    void resetClearsBuffer() {
        parser.feed("event: partial\ndata: incomp");
        parser.reset();
        List<SseParser.SseEvent> events = parser.feed("data: fresh\n\n");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).data()).isEqualTo("fresh");
        assertThat(events.get(0).event()).isNull();
    }

    @Test
    @DisplayName("empty chunk returns empty list")
    void emptyChunkReturnsEmptyList() {
        assertThat(parser.feed("")).isEmpty();
        assertThat(parser.feed(null)).isEmpty();
    }

    @Test
    @DisplayName("data value with colon in it is parsed correctly")
    void dataValueWithColonInValue() {
        List<SseParser.SseEvent> events = parser.feed("data: {\"key\":\"val:ue\"}\n\n");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).data()).isEqualTo("{\"key\":\"val:ue\"}");
    }

    @Test
    @DisplayName("single space after colon is stripped per spec")
    void singleSpaceAfterColonIsStripped() {
        List<SseParser.SseEvent> events = parser.feed("data:  leading space\n\n");
        // Only one space is stripped, so result has one leading space
        assertThat(events).hasSize(1);
        assertThat(events.get(0).data()).isEqualTo(" leading space");
    }

    @Test
    @DisplayName("field with no colon treated as field name with empty value")
    void fieldWithNoColon() {
        // "data" with no colon → data field with empty value
        List<SseParser.SseEvent> events = parser.feed("data\n\n");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).data()).isEmpty();
    }

    @Test
    @DisplayName("multiple data lines joined with newline")
    void multipleDataLinesJoinedWithNewline() {
        List<SseParser.SseEvent> events = parser.feed("data: a\ndata: b\ndata: c\n\n");
        assertThat(events).hasSize(1);
        assertThat(events.get(0).data()).isEqualTo("a\nb\nc");
    }

    @Test
    @DisplayName("event delivered after second feed when first ends mid-empty-line")
    void eventDeliveredAfterSecondFeedMidEmptyLine() {
        List<SseParser.SseEvent> first = parser.feed("data: test\n");
        assertThat(first).isEmpty();
        List<SseParser.SseEvent> second = parser.feed("\n");
        assertThat(second).hasSize(1);
        assertThat(second.get(0).data()).isEqualTo("test");
    }

    @Test
    @DisplayName("benchmark: parse 10 000 events completes within 2 seconds")
    void benchmarkParse10kEvents() {
        String singleEvent = "event: content_block_delta\ndata: {\"type\":\"text_delta\",\"text\":\"hello\"}\n\n";
        StringBuilder big = new StringBuilder();
        for (int i = 0; i < 10_000; i++) big.append(singleEvent);

        long start = System.currentTimeMillis();
        List<SseParser.SseEvent> events = parser.feed(big.toString());
        long elapsed = System.currentTimeMillis() - start;

        assertThat(events).hasSize(10_000);
        assertThat(elapsed).isLessThan(2_000);
    }

    @Test
    @DisplayName("event split into many tiny chunks reassembles correctly")
    void tinyChunksReassemble() {
        String fullEvent = "event: test\ndata: hello world\n\n";
        List<SseParser.SseEvent> allEvents = List.of();
        for (int i = 0; i < fullEvent.length(); i++) {
            List<SseParser.SseEvent> partial = parser.feed(String.valueOf(fullEvent.charAt(i)));
            if (!partial.isEmpty()) {
                allEvents = partial;
            }
        }
        assertThat(allEvents).hasSize(1);
        assertThat(allEvents.get(0).event()).isEqualTo("test");
        assertThat(allEvents.get(0).data()).isEqualTo("hello world");
    }

    @Test
    @DisplayName("two events in separate feeds both dispatched")
    void twoEventsInSeparateFeeds() {
        List<SseParser.SseEvent> e1 = parser.feed("data: one\n\n");
        List<SseParser.SseEvent> e2 = parser.feed("data: two\n\n");
        assertThat(e1).hasSize(1).first().extracting(SseParser.SseEvent::data).isEqualTo("one");
        assertThat(e2).hasSize(1).first().extracting(SseParser.SseEvent::data).isEqualTo("two");
    }
}
