package com.agentforge.runtime.compaction;

import com.agentforge.api.provider.ApiClient;
import com.agentforge.api.provider.ApiRequest;
import com.agentforge.api.stream.AssistantEvent;
import com.agentforge.common.model.ContentBlock;
import com.agentforge.common.model.ConversationMessage;
import com.agentforge.common.model.Session;
import com.agentforge.common.model.TokenUsage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Highest quality compaction — uses an LLM to summarize early messages.
 * Replaces early messages with a single summary, keeps recent messages intact.
 *
 * <p>Estimated cost: 1.0 (requires LLM API call).
 */
public final class LlmSummarizationStrategy implements CompactionStrategy {

    /** Number of recent messages to keep verbatim (not summarized). */
    private static final int RECENT_MESSAGES_TO_KEEP = 10;
    private static final String SUMMARIZATION_MODEL = "claude-haiku-4-5";
    private static final String SUMMARIZATION_PROMPT = """
        You are summarizing a conversation for context compaction. \
        Create a concise summary of the conversation below that preserves \
        key decisions, tool results, and important context. \
        Format as a compact narrative. Do not include preamble.""";

    private final ApiClient apiClient;

    public LlmSummarizationStrategy(ApiClient apiClient) {
        if (apiClient == null) throw new IllegalArgumentException("apiClient must not be null");
        this.apiClient = apiClient;
    }

    @Override
    public Session compact(Session session, int contextWindow) {
        List<ConversationMessage> messages = session.messages();
        if (messages.size() <= RECENT_MESSAGES_TO_KEEP) return session;

        int splitPoint = messages.size() - RECENT_MESSAGES_TO_KEEP;
        List<ConversationMessage> earlyMessages = messages.subList(0, splitPoint);
        List<ConversationMessage> recentMessages = messages.subList(splitPoint, messages.size());

        String summary = summarize(earlyMessages);

        // Build the compacted message list: summary + recent messages
        List<ConversationMessage> compacted = new ArrayList<>();
        compacted.add(ConversationMessage.of("user",
            List.of(new ContentBlock.Text("[CONVERSATION SUMMARY]\n" + summary))));
        compacted.addAll(recentMessages);

        TokenUsage totalUsage = TokenUsage.ZERO;
        for (ConversationMessage msg : compacted) {
            totalUsage = totalUsage.add(msg.usage());
        }

        return new Session(session.version(), session.id(), compacted, totalUsage);
    }

    private String summarize(List<ConversationMessage> messages) {
        StringBuilder conversationText = new StringBuilder();
        for (ConversationMessage msg : messages) {
            conversationText.append(msg.role().toUpperCase()).append(": ");
            for (ContentBlock block : msg.blocks()) {
                switch (block) {
                    case ContentBlock.Text t -> conversationText.append(t.text());
                    case ContentBlock.ToolUse u ->
                        conversationText.append("[Tool: ").append(u.name()).append("(").append(u.inputJson()).append(")]");
                    case ContentBlock.ToolResult r ->
                        conversationText.append("[Result: ").append(r.content()).append("]");
                }
            }
            conversationText.append("\n");
        }

        ApiRequest request = ApiRequest.builder()
            .model(SUMMARIZATION_MODEL)
            .systemPrompt(SUMMARIZATION_PROMPT)
            .addMessage(ConversationMessage.userText(conversationText.toString()))
            .maxTokens(1024)
            .temperature(0.3)
            .build();

        StringBuilder summary = new StringBuilder();
        try (Stream<AssistantEvent> events = apiClient.streamMessage(request)) {
            for (AssistantEvent event : (Iterable<AssistantEvent>) events::iterator) {
                if (event instanceof AssistantEvent.TextDelta delta) {
                    summary.append(delta.text());
                }
            }
        }

        return summary.isEmpty() ? "Previous conversation summarized." : summary.toString();
    }

    @Override
    public String name() {
        return "llm-summarization";
    }

    @Override
    public double estimatedCost() {
        return 1.0;
    }
}
