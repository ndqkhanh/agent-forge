package com.agentforge.api.model;

import com.agentforge.api.provider.ApiClient;
import com.agentforge.common.error.ApiException;
import com.agentforge.common.model.ModelInfo;

import java.util.HashMap;
import java.util.Map;

/**
 * Resolves model aliases and routes requests to the correct {@link ApiClient}.
 *
 * <p>Instances are built via a fluent API. The class is effectively immutable once
 * constructed via the fluent methods (each method returns a new instance).
 *
 * <p><b>Thread safety:</b> Individual instances are immutable after construction.
 * The fluent builder methods return new instances and are safe to call from any thread.
 */
public final class ModelRouter {

    private final Map<String, ModelInfo> models;      // id → ModelInfo
    private final Map<String, ApiClient> providers;   // providerName → ApiClient
    private final Map<String, String> aliases;         // alias → canonical id

    public ModelRouter() {
        this(Map.of(), Map.of(), Map.of());
    }

    private ModelRouter(
        Map<String, ModelInfo> models,
        Map<String, ApiClient> providers,
        Map<String, String> aliases
    ) {
        this.models = Map.copyOf(models);
        this.providers = Map.copyOf(providers);
        this.aliases = Map.copyOf(aliases);
    }

    /**
     * Register a provider and its available models.
     *
     * @return new ModelRouter with the provider added
     */
    public ModelRouter withProvider(ApiClient provider) {
        Map<String, ApiClient> newProviders = new HashMap<>(providers);
        newProviders.put(provider.providerName(), provider);

        Map<String, ModelInfo> newModels = new HashMap<>(models);
        for (ModelInfo m : provider.availableModels()) {
            newModels.put(m.id(), m);
        }

        return new ModelRouter(newModels, newProviders, aliases);
    }

    /**
     * Register a short alias for a canonical model ID.
     *
     * @param alias   short alias (e.g. "opus")
     * @param modelId canonical model ID (e.g. "claude-opus-4-6")
     * @return new ModelRouter with the alias added
     */
    public ModelRouter withAlias(String alias, String modelId) {
        Map<String, String> newAliases = new HashMap<>(aliases);
        newAliases.put(alias, modelId);
        return new ModelRouter(models, providers, newAliases);
    }

    /**
     * Resolve an alias or canonical model ID to a {@link ModelInfo}.
     *
     * @param aliasOrId alias or canonical model ID
     * @return the resolved ModelInfo
     * @throws ApiException if the model cannot be resolved
     */
    public ModelInfo resolve(String aliasOrId) {
        if (aliasOrId == null || aliasOrId.isBlank()) {
            throw new ApiException("Model identifier must not be blank");
        }

        // Check aliases first
        String canonicalId = aliases.getOrDefault(aliasOrId, aliasOrId);

        ModelInfo info = models.get(canonicalId);
        if (info != null) return info;

        // Fall back to catalog
        info = ModelCatalog.findById(canonicalId);
        if (info != null) return info;

        throw new ApiException("Unknown model: '" + aliasOrId + "'");
    }

    /**
     * Get the {@link ApiClient} responsible for the given model.
     *
     * @param model the resolved ModelInfo
     * @return the provider client
     * @throws ApiException if no provider is registered for the model's provider name
     */
    public ApiClient providerFor(ModelInfo model) {
        ApiClient client = providers.get(model.provider());
        if (client == null) {
            throw new ApiException("No provider registered for: '" + model.provider() + "'");
        }
        return client;
    }

    /** @return snapshot of registered model IDs (unmodifiable) */
    public Map<String, ModelInfo> models() {
        return models;
    }

    /** @return snapshot of registered aliases (unmodifiable) */
    public Map<String, String> aliases() {
        return aliases;
    }
}
