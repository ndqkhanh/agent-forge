package com.agentforge.runtime.usage;

import com.agentforge.common.model.ModelInfo;
import com.agentforge.common.model.TokenUsage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

class UsageTrackerTest {

    private UsageTracker tracker;

    @BeforeEach
    void setUp() {
        tracker = new UsageTracker();
    }

    @Test
    @DisplayName("track single model usage accumulates correctly")
    void track_singleModel_accumulatesUsage() {
        tracker.track("model-a", TokenUsage.of(100, 50));
        tracker.track("model-a", TokenUsage.of(200, 75));
        TokenUsage usage = tracker.usageByModel().get("model-a");
        assertThat(usage.inputTokens()).isEqualTo(300);
        assertThat(usage.outputTokens()).isEqualTo(125);
    }

    @Test
    @DisplayName("track multiple models keeps separate totals")
    void track_multipleModels_separateTotals() {
        tracker.track("model-a", TokenUsage.of(100, 50));
        tracker.track("model-b", TokenUsage.of(200, 80));
        assertThat(tracker.usageByModel()).hasSize(2);
        assertThat(tracker.usageByModel().get("model-a").inputTokens()).isEqualTo(100);
        assertThat(tracker.usageByModel().get("model-b").inputTokens()).isEqualTo(200);
    }

    @Test
    @DisplayName("totalUsage sums all models")
    void totalUsage_sumsAllModels() {
        tracker.track("model-a", TokenUsage.of(100, 50));
        tracker.track("model-b", TokenUsage.of(200, 80));
        TokenUsage total = tracker.totalUsage();
        assertThat(total.inputTokens()).isEqualTo(300);
        assertThat(total.outputTokens()).isEqualTo(130);
    }

    @Test
    @DisplayName("totalUsage with no tracking returns ZERO")
    void totalUsage_noTracking_returnsZero() {
        assertThat(tracker.totalUsage()).isEqualTo(TokenUsage.ZERO);
    }

    @Test
    @DisplayName("totalCost with no model info returns 0")
    void totalCost_noModelInfo_returnsZero() {
        tracker.track("model-a", TokenUsage.of(1000, 500));
        assertThat(tracker.totalCost()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("totalCost calculates correctly using model pricing")
    void totalCost_withModelInfo_calculatesCost() {
        ModelInfo info = new ModelInfo("model-a", "Model A", "anthropic", 200_000, 3.0, 15.0);
        tracker.registerModel(info);
        tracker.track("model-a", TokenUsage.of(1000, 500));
        // cost = (1000 * 3.0 / 1000) + (500 * 15.0 / 1000) = 3.0 + 7.5 = 10.5
        assertThat(tracker.totalCost()).isCloseTo(10.5, within(0.001));
    }

    @Test
    @DisplayName("summary contains model names and totals")
    void summary_containsModelNamesAndTotals() {
        tracker.track("claude-sonnet", TokenUsage.of(500, 200));
        String summary = tracker.summary();
        assertThat(summary).contains("claude-sonnet");
        assertThat(summary).contains("500");
        assertThat(summary).contains("200");
    }

    @Test
    @DisplayName("summary with no usage returns descriptive message")
    void summary_noUsage_returnsDescriptiveMessage() {
        assertThat(tracker.summary()).contains("No usage recorded");
    }

    @Test
    @DisplayName("track null modelId throws IllegalArgumentException")
    void track_nullModelId_throws() {
        assertThatThrownBy(() -> tracker.track(null, TokenUsage.ZERO))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("track null usage throws IllegalArgumentException")
    void track_nullUsage_throws() {
        assertThatThrownBy(() -> tracker.track("model-a", null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("usageByModel returns unmodifiable copy")
    void usageByModel_returnsDefensiveCopy() {
        tracker.track("model-a", TokenUsage.of(100, 50));
        var map = tracker.usageByModel();
        assertThatThrownBy(() -> map.put("model-b", TokenUsage.ZERO))
            .isInstanceOf(UnsupportedOperationException.class);
    }
}
