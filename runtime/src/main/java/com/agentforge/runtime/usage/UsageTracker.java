package com.agentforge.runtime.usage;

import com.agentforge.common.model.ModelInfo;
import com.agentforge.common.model.TokenUsage;

import java.util.HashMap;
import java.util.Map;

/**
 * Tracks cumulative token usage across multiple API calls and models.
 * Not thread-safe — designed for single-threaded use within a ConversationRuntime.
 */
public final class UsageTracker {

    private final Map<String, TokenUsage> usageByModel;
    private final Map<String, ModelInfo> modelInfos;

    public UsageTracker() {
        this.usageByModel = new HashMap<>();
        this.modelInfos = new HashMap<>();
    }

    /**
     * Record token usage for a specific model.
     *
     * @param modelId the model identifier
     * @param usage   the usage to accumulate
     */
    public void track(String modelId, TokenUsage usage) {
        if (modelId == null || modelId.isBlank()) throw new IllegalArgumentException("modelId must not be blank");
        if (usage == null) throw new IllegalArgumentException("usage must not be null");
        usageByModel.merge(modelId, usage, TokenUsage::add);
    }

    /**
     * Register model pricing info for cost calculation.
     *
     * @param modelInfo the model metadata including cost per token
     */
    public void registerModel(ModelInfo modelInfo) {
        if (modelInfo == null) throw new IllegalArgumentException("modelInfo must not be null");
        modelInfos.put(modelInfo.id(), modelInfo);
    }

    /**
     * Return the total token usage aggregated across all models.
     */
    public TokenUsage totalUsage() {
        TokenUsage total = TokenUsage.ZERO;
        for (TokenUsage u : usageByModel.values()) {
            total = total.add(u);
        }
        return total;
    }

    /**
     * Return total estimated cost in USD across all tracked models.
     * Models without registered pricing info contribute $0.
     */
    public double totalCost() {
        double cost = 0.0;
        for (Map.Entry<String, TokenUsage> entry : usageByModel.entrySet()) {
            ModelInfo info = modelInfos.get(entry.getKey());
            if (info != null) {
                cost += info.estimateCost(entry.getValue());
            }
        }
        return cost;
    }

    /**
     * Return a defensive copy of per-model usage breakdown.
     */
    public Map<String, TokenUsage> usageByModel() {
        return Map.copyOf(usageByModel);
    }

    /**
     * Return a human-readable summary of usage and costs.
     */
    public String summary() {
        if (usageByModel.isEmpty()) {
            return "No usage recorded.";
        }

        StringBuilder sb = new StringBuilder("Token Usage Summary:\n");
        for (Map.Entry<String, TokenUsage> entry : usageByModel.entrySet()) {
            String modelId = entry.getKey();
            TokenUsage usage = entry.getValue();
            sb.append(String.format("  %s: %d in / %d out", modelId, usage.inputTokens(), usage.outputTokens()));
            ModelInfo info = modelInfos.get(modelId);
            if (info != null) {
                sb.append(String.format(" ($%.4f)", info.estimateCost(usage)));
            }
            sb.append("\n");
        }

        TokenUsage total = totalUsage();
        sb.append(String.format("  TOTAL: %d in / %d out / %d total",
            total.inputTokens(), total.outputTokens(), total.totalTokens()));

        double cost = totalCost();
        if (cost > 0) {
            sb.append(String.format(" ($%.4f)", cost));
        }

        return sb.toString();
    }
}
