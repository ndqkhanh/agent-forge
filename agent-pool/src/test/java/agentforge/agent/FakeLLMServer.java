package agentforge.agent;

import java.util.List;
import java.util.function.Function;

/**
 * Fake LLM transport for testing — executes a handler function
 * instead of making real HTTP calls.
 */
public final class FakeLLMServer implements LLMClient.LLMTransport {

    private final Function<LLMClient.LLMRequest, String> handler;
    private List<String> streamTokens = List.of();

    public FakeLLMServer(Function<LLMClient.LLMRequest, String> handler) {
        this.handler = handler;
    }

    public void setStreamTokens(List<String> tokens) {
        this.streamTokens = List.copyOf(tokens);
    }

    @Override
    public String complete(LLMClient.LLMRequest request) {
        return handler.apply(request);
    }

    @Override
    public List<String> stream(LLMClient.LLMRequest request) {
        return streamTokens;
    }
}
