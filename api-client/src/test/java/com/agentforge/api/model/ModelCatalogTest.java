package com.agentforge.api.model;

import com.agentforge.common.model.ModelInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("ModelCatalog")
class ModelCatalogTest {

    @Test
    @DisplayName("all() returns all five known models")
    void allReturnsAllModels() {
        List<ModelInfo> models = ModelCatalog.all();
        assertThat(models).hasSize(5);
    }

    @Test
    @DisplayName("all models have non-blank id, displayName, and provider")
    void allModelsHaveValidFields() {
        for (ModelInfo m : ModelCatalog.all()) {
            assertThat(m.id()).as("id for " + m.displayName()).isNotBlank();
            assertThat(m.displayName()).as("displayName").isNotBlank();
            assertThat(m.provider()).as("provider for " + m.id()).isNotBlank();
            assertThat(m.contextWindow()).as("contextWindow for " + m.id()).isGreaterThan(0);
            assertThat(m.inputCostPer1kTokens()).as("inputCost for " + m.id()).isGreaterThanOrEqualTo(0);
            assertThat(m.outputCostPer1kTokens()).as("outputCost for " + m.id()).isGreaterThanOrEqualTo(0);
        }
    }

    @Test
    @DisplayName("default aliases all resolve to known model IDs")
    void defaultAliasesResolveToKnownIds() {
        for (var entry : ModelCatalog.DEFAULT_ALIASES.entrySet()) {
            ModelInfo found = ModelCatalog.findById(entry.getValue());
            assertThat(found)
                .as("alias '%s' → '%s'", entry.getKey(), entry.getValue())
                .isNotNull();
        }
    }

    @Test
    @DisplayName("findById returns correct model for each catalog entry")
    void findByIdReturnsCorrectModel() {
        assertThat(ModelCatalog.findById("claude-opus-4-6")).isEqualTo(ModelCatalog.CLAUDE_OPUS);
        assertThat(ModelCatalog.findById("claude-sonnet-4-6")).isEqualTo(ModelCatalog.CLAUDE_SONNET);
        assertThat(ModelCatalog.findById("claude-haiku-4-5-20251001")).isEqualTo(ModelCatalog.CLAUDE_HAIKU);
        assertThat(ModelCatalog.findById("gpt-4o")).isEqualTo(ModelCatalog.GPT_4O);
        assertThat(ModelCatalog.findById("gpt-4o-mini")).isEqualTo(ModelCatalog.GPT_4O_MINI);
    }

    @Test
    @DisplayName("findById returns null for unknown model ID")
    void findByIdReturnsNullForUnknown() {
        assertThat(ModelCatalog.findById("totally-unknown-model")).isNull();
    }

    @Test
    @DisplayName("Anthropic models have correct provider name")
    void anthropicModelsHaveCorrectProvider() {
        assertThat(ModelCatalog.CLAUDE_OPUS.provider()).isEqualTo("anthropic");
        assertThat(ModelCatalog.CLAUDE_SONNET.provider()).isEqualTo("anthropic");
        assertThat(ModelCatalog.CLAUDE_HAIKU.provider()).isEqualTo("anthropic");
    }

    @Test
    @DisplayName("OpenAI models have correct provider name")
    void openAiModelsHaveCorrectProvider() {
        assertThat(ModelCatalog.GPT_4O.provider()).isEqualTo("openai");
        assertThat(ModelCatalog.GPT_4O_MINI.provider()).isEqualTo("openai");
    }

    @Test
    @DisplayName("all() returns immutable list")
    void allReturnsImmutableList() {
        List<ModelInfo> models = ModelCatalog.all();
        assertThatThrownBy(() -> models.add(ModelCatalog.CLAUDE_OPUS))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
