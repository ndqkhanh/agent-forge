package com.agentforge.api.model;

import com.agentforge.api.provider.ApiClient;
import com.agentforge.api.provider.ApiRequest;
import com.agentforge.api.stream.AssistantEvent;
import com.agentforge.common.error.ApiException;
import com.agentforge.common.model.ModelInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ModelRouter")
class ModelRouterTest {

    // Minimal stub ApiClient for testing
    private static ApiClient stubProvider(String name, List<ModelInfo> models) {
        return new ApiClient() {
            @Override public Stream<AssistantEvent> streamMessage(ApiRequest r) { return Stream.empty(); }
            @Override public String providerName() { return name; }
            @Override public List<ModelInfo> availableModels() { return models; }
        };
    }

    @Test
    @DisplayName("resolve known alias returns correct ModelInfo")
    void resolveKnownAlias() {
        ModelRouter router = new ModelRouter()
            .withAlias("opus", "claude-opus-4-6");

        ModelInfo resolved = router.resolve("opus");
        assertThat(resolved.id()).isEqualTo("claude-opus-4-6");
    }

    @Test
    @DisplayName("resolve canonical model ID directly (from catalog)")
    void resolveFullModelId() {
        ModelRouter router = new ModelRouter();
        ModelInfo resolved = router.resolve("claude-sonnet-4-6");
        assertThat(resolved.id()).isEqualTo("claude-sonnet-4-6");
        assertThat(resolved.provider()).isEqualTo("anthropic");
    }

    @Test
    @DisplayName("resolve unknown alias throws ApiException")
    void resolveUnknownAliasThrows() {
        ModelRouter router = new ModelRouter();
        assertThatThrownBy(() -> router.resolve("unknown-model-xyz"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("unknown-model-xyz");
    }

    @Test
    @DisplayName("blank model id throws ApiException")
    void blankModelIdThrows() {
        ModelRouter router = new ModelRouter();
        assertThatThrownBy(() -> router.resolve(""))
            .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("null model id throws ApiException")
    void nullModelIdThrows() {
        ModelRouter router = new ModelRouter();
        assertThatThrownBy(() -> router.resolve(null))
            .isInstanceOf(ApiException.class);
    }

    @Test
    @DisplayName("providerFor returns correct client after withProvider")
    void providerLookup() {
        ModelInfo model = ModelCatalog.CLAUDE_OPUS;
        ApiClient client = stubProvider("anthropic", List.of(model));
        ModelRouter router = new ModelRouter().withProvider(client);

        ApiClient found = router.providerFor(model);
        assertThat(found.providerName()).isEqualTo("anthropic");
    }

    @Test
    @DisplayName("providerFor unregistered provider throws ApiException")
    void providerForUnregisteredThrows() {
        ModelRouter router = new ModelRouter();
        ModelInfo model = ModelCatalog.CLAUDE_OPUS;
        assertThatThrownBy(() -> router.providerFor(model))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("anthropic");
    }

    @Test
    @DisplayName("withProvider registers models under their IDs")
    void withProviderRegistersModels() {
        ModelInfo m = new ModelInfo("custom-model", "Custom", "custom", 10_000, 1.0, 2.0);
        ApiClient client = stubProvider("custom", List.of(m));
        ModelRouter router = new ModelRouter().withProvider(client);

        ModelInfo resolved = router.resolve("custom-model");
        assertThat(resolved.displayName()).isEqualTo("Custom");
    }

    @Test
    @DisplayName("withAlias registers custom alias")
    void withAliasRegistersCustomAlias() {
        ModelInfo m = new ModelInfo("my-model", "My Model", "myprovider", 10_000, 1.0, 2.0);
        ApiClient client = stubProvider("myprovider", List.of(m));
        ModelRouter router = new ModelRouter()
            .withProvider(client)
            .withAlias("myalias", "my-model");

        ModelInfo resolved = router.resolve("myalias");
        assertThat(resolved.id()).isEqualTo("my-model");
    }

    @Test
    @DisplayName("withProvider and withAlias return new instances (immutable builder pattern)")
    void fluentMethodsReturnNewInstances() {
        ModelRouter base = new ModelRouter();
        ApiClient client = stubProvider("anthropic", List.of(ModelCatalog.CLAUDE_HAIKU));
        ModelRouter withProv = base.withProvider(client);
        ModelRouter withAlias = withProv.withAlias("haiku", "claude-haiku-4-5-20251001");

        assertThat(base).isNotSameAs(withProv);
        assertThat(withProv).isNotSameAs(withAlias);
        // base should not have the provider
        assertThatThrownBy(() -> base.providerFor(ModelCatalog.CLAUDE_HAIKU))
            .isInstanceOf(ApiException.class);
    }
}
