package com.agentforge.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SseWriterTest {

    @Test
    void write_producesCorrectSseFrame() throws IOException {
        var out = new ByteArrayOutputStream();
        var writer = new SseWriter(out);

        writer.write("message", "{\"text\":\"hello\"}");

        String result = out.toString(StandardCharsets.UTF_8);
        assertThat(result).isEqualTo("event: message\ndata: {\"text\":\"hello\"}\n\n");
    }

    @Test
    void write_textEvent_producesCorrectFrame() throws IOException {
        var out = new ByteArrayOutputStream();
        var writer = new SseWriter(out);

        writer.write("text", "hello world");

        String result = out.toString(StandardCharsets.UTF_8);
        assertThat(result).isEqualTo("event: text\ndata: hello world\n\n");
    }

    @Test
    void write_doneEvent_producesCorrectFrame() throws IOException {
        var out = new ByteArrayOutputStream();
        var writer = new SseWriter(out);

        writer.write("done", "{}");

        String result = out.toString(StandardCharsets.UTF_8);
        assertThat(result).startsWith("event: done\n");
        assertThat(result).contains("data: {}");
        assertThat(result).endsWith("\n\n");
    }

    @Test
    void writeComment_producesCommentFrame() throws IOException {
        var out = new ByteArrayOutputStream();
        var writer = new SseWriter(out);

        writer.writeComment("keep-alive");

        String result = out.toString(StandardCharsets.UTF_8);
        assertThat(result).isEqualTo(": keep-alive\n\n");
    }

    @Test
    void writeComment_nullComment_producesEmptyComment() throws IOException {
        var out = new ByteArrayOutputStream();
        var writer = new SseWriter(out);

        writer.writeComment(null);

        String result = out.toString(StandardCharsets.UTF_8);
        assertThat(result).isEqualTo(": \n\n");
    }

    @Test
    void write_multipleEvents_appendedSequentially() throws IOException {
        var out = new ByteArrayOutputStream();
        var writer = new SseWriter(out);

        writer.write("start", "{}");
        writer.write("data", "{\"n\":1}");
        writer.write("end", "{}");

        String result = out.toString(StandardCharsets.UTF_8);
        assertThat(result).isEqualTo(
            "event: start\ndata: {}\n\n" +
            "event: data\ndata: {\"n\":1}\n\n" +
            "event: end\ndata: {}\n\n"
        );
    }

    @Test
    void constructor_nullStream_throwsIllegalArgument() {
        assertThatThrownBy(() -> new SseWriter(null))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("out");
    }

    @Test
    void write_nullEventType_throwsIllegalArgument() throws IOException {
        var out = new ByteArrayOutputStream();
        var writer = new SseWriter(out);

        assertThatThrownBy(() -> writer.write(null, "data"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void write_blankEventType_throwsIllegalArgument() {
        var out = new ByteArrayOutputStream();
        var writer = new SseWriter(out);

        assertThatThrownBy(() -> writer.write("  ", "data"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void write_nullData_throwsIllegalArgument() {
        var out = new ByteArrayOutputStream();
        var writer = new SseWriter(out);

        assertThatThrownBy(() -> writer.write("event", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formatFrame_returnsCorrectString() {
        String frame = SseWriter.formatFrame("message", "{\"x\":1}");
        assertThat(frame).isEqualTo("event: message\ndata: {\"x\":1}\n\n");
    }

    @Test
    void formatFrame_nullEventType_throwsIllegalArgument() {
        assertThatThrownBy(() -> SseWriter.formatFrame(null, "data"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void formatFrame_nullData_throwsIllegalArgument() {
        assertThatThrownBy(() -> SseWriter.formatFrame("event", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @ParameterizedTest
    @CsvSource({"text,hello", "error,oops", "done,{}", "connected,{\"id\":\"abc\"}"})
    void formatFrame_variousEventTypes_alwaysHasDoubleNewlineTerminator(String eventType, String data) {
        String frame = SseWriter.formatFrame(eventType, data);
        assertThat(frame).endsWith("\n\n");
        assertThat(frame).startsWith("event: " + eventType);
    }
}
