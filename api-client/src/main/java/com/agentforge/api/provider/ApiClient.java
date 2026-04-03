package com.agentforge.api.provider;

import com.agentforge.api.stream.AssistantEvent;
import com.agentforge.common.model.ModelInfo;

import java.util.List;
import java.util.stream.Stream;

/**
 * Core abstraction for interacting with an LLM provider.
 *
 * <p>Implementations must be thread-safe: multiple threads may call
 * {@link #streamMessage(ApiRequest)} concurrently, each receiving an independent stream.
 */
public interface ApiClient {

    /**
     * Stream a message to the LLM, yielding events as they arrive.
     *
     * <p>The returned {@link Stream} is lazy — the HTTP call is made when the stream is
     * first consumed. Callers are responsible for closing the stream.
     *
     * @param request the API request
     * @return a stream of {@link AssistantEvent}s in arrival order
     * @throws com.agentforge.common.error.ApiException on non-retryable API errors
     */
    Stream<AssistantEvent> streamMessage(ApiRequest request);

    /**
     * @return the provider name, e.g. "anthropic" or "openai"
     */
    String providerName();

    /**
     * @return the list of models supported by this provider
     */
    List<ModelInfo> availableModels();
}
