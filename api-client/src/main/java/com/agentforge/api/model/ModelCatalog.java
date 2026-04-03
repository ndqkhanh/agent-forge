package com.agentforge.api.model;

import com.agentforge.common.model.ModelInfo;

import java.util.List;
import java.util.Map;

/**
 * Static catalog of well-known LLM models and their default aliases.
 *
 * <p>Thread-safe. All fields are static final immutable constants.
 */
public final class ModelCatalog {

    // Anthropic models
    public static final ModelInfo CLAUDE_OPUS = new ModelInfo(
        "claude-opus-4-6", "Claude Opus 4.6", "anthropic", 200_000, 15.0, 75.0);

    public static final ModelInfo CLAUDE_SONNET = new ModelInfo(
        "claude-sonnet-4-6", "Claude Sonnet 4.6", "anthropic", 200_000, 3.0, 15.0);

    public static final ModelInfo CLAUDE_HAIKU = new ModelInfo(
        "claude-haiku-4-5-20251001", "Claude Haiku 4.5", "anthropic", 200_000, 0.80, 4.0);

    // OpenAI models
    public static final ModelInfo GPT_4O = new ModelInfo(
        "gpt-4o", "GPT-4o", "openai", 128_000, 2.50, 10.0);

    public static final ModelInfo GPT_4O_MINI = new ModelInfo(
        "gpt-4o-mini", "GPT-4o Mini", "openai", 128_000, 0.15, 0.60);

    /** Default short aliases → canonical model IDs. */
    public static final Map<String, String> DEFAULT_ALIASES = Map.of(
        "opus",      "claude-opus-4-6",
        "sonnet",    "claude-sonnet-4-6",
        "haiku",     "claude-haiku-4-5-20251001",
        "gpt4",      "gpt-4o",
        "gpt4-mini", "gpt-4o-mini"
    );

    private static final List<ModelInfo> ALL = List.of(
        CLAUDE_OPUS, CLAUDE_SONNET, CLAUDE_HAIKU,
        GPT_4O, GPT_4O_MINI
    );

    private ModelCatalog() {}

    /** @return all known models as an immutable list. */
    public static List<ModelInfo> all() {
        return ALL;
    }

    /**
     * Look up a model by its canonical ID.
     *
     * @param id canonical model ID
     * @return the ModelInfo, or null if not found
     */
    public static ModelInfo findById(String id) {
        for (ModelInfo m : ALL) {
            if (m.id().equals(id)) return m;
        }
        return null;
    }
}
