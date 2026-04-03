package com.agentforge.common.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.assertj.core.api.Assertions.*;

class ContentBlockTest {

    @Test
    @DisplayName("Text block stores text content")
    void textBlock() {
        var block = ContentBlock.text("hello");
        assertThat(block).isInstanceOf(ContentBlock.Text.class);
        assertThat(((ContentBlock.Text) block).text()).isEqualTo("hello");
    }

    @Test
    @DisplayName("Text block rejects null text")
    void textBlockRejectsNull() {
        assertThatThrownBy(() -> ContentBlock.text(null))
            .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("ToolUse block stores id, name, and inputJson")
    void toolUseBlock() {
        var block = ContentBlock.toolUse("tc_1", "read_file", "{\"path\":\"/tmp\"}");
        assertThat(block).isInstanceOf(ContentBlock.ToolUse.class);
        var tu = (ContentBlock.ToolUse) block;
        assertThat(tu.id()).isEqualTo("tc_1");
        assertThat(tu.name()).isEqualTo("read_file");
        assertThat(tu.inputJson()).isEqualTo("{\"path\":\"/tmp\"}");
    }

    @Test
    @DisplayName("ToolUse inputJson defaults to empty object when null")
    void toolUseNullInput() {
        var block = new ContentBlock.ToolUse("tc_1", "test", null);
        assertThat(block.inputJson()).isEqualTo("{}");
    }

    @Test
    @DisplayName("ToolResult block stores toolUseId, content, and isError")
    void toolResultBlock() {
        var block = ContentBlock.toolResult("tc_1", "output data", false);
        assertThat(block).isInstanceOf(ContentBlock.ToolResult.class);
        var tr = (ContentBlock.ToolResult) block;
        assertThat(tr.toolUseId()).isEqualTo("tc_1");
        assertThat(tr.content()).isEqualTo("output data");
        assertThat(tr.isError()).isFalse();
    }

    @Test
    @DisplayName("ToolResult with error flag")
    void toolResultError() {
        var block = ContentBlock.toolResult("tc_1", "error msg", true);
        assertThat(((ContentBlock.ToolResult) block).isError()).isTrue();
    }

    @Test
    @DisplayName("Sealed interface allows exhaustive pattern matching")
    void sealedPatternMatching() {
        ContentBlock block = ContentBlock.text("test");
        String result = switch (block) {
            case ContentBlock.Text t -> "text:" + t.text();
            case ContentBlock.ToolUse tu -> "tool:" + tu.name();
            case ContentBlock.ToolResult tr -> "result:" + tr.toolUseId();
        };
        assertThat(result).isEqualTo("text:test");
    }
}
