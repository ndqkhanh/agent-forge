package com.agentforge.common.json;

import com.agentforge.common.model.ContentBlock;
import com.agentforge.common.model.ConversationMessage;
import com.agentforge.common.model.Session;
import com.agentforge.common.model.TokenUsage;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionSerializerTest {

    @Test
    void serialize_emptySession_producesValidJson() {
        Session session = Session.empty("test-id");
        String json = SessionSerializer.serialize(session);
        assertThat(json).contains("\"id\"");
        assertThat(json).contains("test-id");
    }

    @Test
    void roundTrip_emptySession() {
        Session original = Session.empty("abc-123");
        String json = SessionSerializer.serialize(original);
        Session restored = SessionSerializer.deserialize(json);
        assertThat(restored.id()).isEqualTo(original.id());
        assertThat(restored.version()).isEqualTo(original.version());
        assertThat(restored.messageCount()).isZero();
        assertThat(restored.totalUsage()).isEqualTo(TokenUsage.ZERO);
    }

    @Test
    void roundTrip_sessionWithUserMessage() {
        Session session = Session.empty("s1")
                .addMessage(ConversationMessage.userText("hello"));
        String json = SessionSerializer.serialize(session);
        Session restored = SessionSerializer.deserialize(json);
        assertThat(restored.messageCount()).isEqualTo(1);
        assertThat(restored.messages().get(0).role()).isEqualTo("user");
        assertThat(restored.messages().get(0).textContent()).isEqualTo("hello");
    }

    @Test
    void roundTrip_sessionWithMultipleMessages() {
        Session session = Session.empty("s2")
                .addMessage(ConversationMessage.userText("hello"))
                .addMessage(ConversationMessage.assistantText("hi there"));
        String json = SessionSerializer.serialize(session);
        Session restored = SessionSerializer.deserialize(json);
        assertThat(restored.messageCount()).isEqualTo(2);
        assertThat(restored.messages().get(1).role()).isEqualTo("assistant");
    }

    @Test
    void roundTrip_toolUseBlock() {
        ContentBlock.ToolUse toolUse = new ContentBlock.ToolUse("tool-1", "calculator", "{\"x\":5}");
        ConversationMessage msg = new ConversationMessage("assistant",
                List.of(toolUse), TokenUsage.ZERO);
        Session session = Session.empty("s3").addMessage(msg);
        String json = SessionSerializer.serialize(session);
        Session restored = SessionSerializer.deserialize(json);
        ContentBlock block = restored.messages().get(0).blocks().get(0);
        assertThat(block).isInstanceOf(ContentBlock.ToolUse.class);
        ContentBlock.ToolUse restoredTool = (ContentBlock.ToolUse) block;
        assertThat(restoredTool.id()).isEqualTo("tool-1");
        assertThat(restoredTool.name()).isEqualTo("calculator");
        assertThat(restoredTool.inputJson()).isEqualTo("{\"x\":5}");
    }

    @Test
    void roundTrip_toolResultBlock() {
        ContentBlock.ToolResult result = ContentBlock.ToolResult.success("tool-1", "42");
        ConversationMessage msg = new ConversationMessage("tool",
                List.of(result), TokenUsage.ZERO);
        Session session = Session.empty("s4").addMessage(msg);
        String json = SessionSerializer.serialize(session);
        Session restored = SessionSerializer.deserialize(json);
        ContentBlock block = restored.messages().get(0).blocks().get(0);
        assertThat(block).isInstanceOf(ContentBlock.ToolResult.class);
        ContentBlock.ToolResult restoredResult = (ContentBlock.ToolResult) block;
        assertThat(restoredResult.toolUseId()).isEqualTo("tool-1");
        assertThat(restoredResult.content()).isEqualTo("42");
        assertThat(restoredResult.isError()).isFalse();
    }

    @Test
    void roundTrip_errorToolResult() {
        ContentBlock.ToolResult errResult = ContentBlock.ToolResult.error("t1", "failed");
        ConversationMessage msg = new ConversationMessage("tool",
                List.of(errResult), TokenUsage.ZERO);
        Session session = Session.empty("s5").addMessage(msg);
        String json = SessionSerializer.serialize(session);
        Session restored = SessionSerializer.deserialize(json);
        ContentBlock.ToolResult restoredBlock =
                (ContentBlock.ToolResult) restored.messages().get(0).blocks().get(0);
        assertThat(restoredBlock.isError()).isTrue();
    }

    @Test
    void roundTrip_tokenUsagePreserved() {
        TokenUsage usage = new TokenUsage(100, 50, 20, 10);
        ConversationMessage msg = new ConversationMessage("user",
                List.of(new ContentBlock.Text("hi")), usage);
        Session session = Session.empty("s6").addMessage(msg);
        String json = SessionSerializer.serialize(session);
        Session restored = SessionSerializer.deserialize(json);
        assertThat(restored.totalUsage().inputTokens()).isEqualTo(100);
        assertThat(restored.totalUsage().outputTokens()).isEqualTo(50);
        assertThat(restored.totalUsage().cacheReadTokens()).isEqualTo(20);
        assertThat(restored.totalUsage().cacheWriteTokens()).isEqualTo(10);
    }

    @Test
    void deserialize_invalidJson_throws() {
        assertThatThrownBy(() -> SessionSerializer.deserialize("{invalid"))
                .isInstanceOf(JsonParseException.class);
    }

    @Test
    void deserialize_notAnObject_throws() {
        assertThatThrownBy(() -> SessionSerializer.deserialize("[1,2,3]"))
                .isInstanceOf(JsonParseException.class);
    }

    @Test
    void deserialize_missingId_throws() {
        assertThatThrownBy(() -> SessionSerializer.deserialize("{\"version\":1}"))
                .isInstanceOf(JsonParseException.class);
    }
}
