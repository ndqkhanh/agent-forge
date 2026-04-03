package com.agentforge.common.model;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConversationMessageTest {

    @Test
    void userText_factory_setsRoleAndBlock() {
        ConversationMessage msg = ConversationMessage.userText("hello");
        assertThat(msg.role()).isEqualTo("user");
        assertThat(msg.blocks()).hasSize(1);
        assertThat(msg.blocks().get(0)).isInstanceOf(ContentBlock.Text.class);
        assertThat(((ContentBlock.Text) msg.blocks().get(0)).text()).isEqualTo("hello");
    }

    @Test
    void assistantText_factory_setsRoleAndBlock() {
        ConversationMessage msg = ConversationMessage.assistantText("response");
        assertThat(msg.role()).isEqualTo("assistant");
        assertThat(msg.blocks()).hasSize(1);
    }

    @Test
    void userText_usageIsZero() {
        ConversationMessage msg = ConversationMessage.userText("hi");
        assertThat(msg.usage()).isEqualTo(TokenUsage.ZERO);
    }

    @Test
    void textContent_concatenatesTextBlocks() {
        ConversationMessage msg = ConversationMessage.userText("hello");
        assertThat(msg.textContent()).isEqualTo("hello");
    }

    @Test
    void textContent_skipsNonTextBlocks() {
        List<ContentBlock> blocks = List.of(
                new ContentBlock.Text("part1"),
                new ContentBlock.ToolUse("id", "tool", "{}"),
                new ContentBlock.Text("part2")
        );
        ConversationMessage msg = new ConversationMessage("user", blocks, TokenUsage.ZERO);
        assertThat(msg.textContent()).isEqualTo("part1part2");
    }

    @Test
    void defensiveCopy_blocksAreImmutable() {
        List<ContentBlock> mutable = new ArrayList<>();
        mutable.add(new ContentBlock.Text("first"));
        ConversationMessage msg = new ConversationMessage("user", mutable, TokenUsage.ZERO);
        mutable.add(new ContentBlock.Text("second"));
        assertThat(msg.blocks()).hasSize(1);
    }

    @Test
    void blocks_listIsUnmodifiable() {
        ConversationMessage msg = ConversationMessage.userText("hi");
        assertThatThrownBy(() -> msg.blocks().add(new ContentBlock.Text("extra")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void nullRole_throws() {
        assertThatThrownBy(() -> new ConversationMessage(null, List.of(), TokenUsage.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void of_factory_setsUsageToZero() {
        ConversationMessage msg = ConversationMessage.of("system", List.of(new ContentBlock.Text("sys")));
        assertThat(msg.usage()).isEqualTo(TokenUsage.ZERO);
    }
}
