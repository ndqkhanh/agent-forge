package com.agentforge.common.model;

import java.util.ArrayList;
import java.util.List;

public record Session(int version, String id, List<ConversationMessage> messages, TokenUsage totalUsage) {

    public static final int CURRENT_VERSION = 1;

    public Session {
        messages = List.copyOf(messages);
    }

    public Session addMessage(ConversationMessage msg) {
        List<ConversationMessage> newMessages = new ArrayList<>(messages);
        newMessages.add(msg);
        return new Session(version, id, newMessages, totalUsage.add(msg.usage()));
    }

    public int messageCount() {
        return messages.size();
    }

    public static Session empty(String id) {
        return new Session(CURRENT_VERSION, id, List.of(), TokenUsage.ZERO);
    }
}
