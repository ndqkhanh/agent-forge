package com.agentforge.runtime.prompt;

import com.agentforge.common.model.ToolSchema;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Builds the system prompt for a conversation turn.
 * Combines a base prompt, tool descriptions, and optional instruction files (CLAUDE.md, etc.).
 */
public final class SystemPromptBuilder {

    /** Maximum characters to include from instruction files (~12KB). */
    private static final int MAX_INSTRUCTION_CHARS = 12_288;

    private final String basePrompt;
    private final List<String> instructionFilePaths;

    private SystemPromptBuilder(Builder builder) {
        this.basePrompt = builder.basePrompt;
        this.instructionFilePaths = List.copyOf(builder.instructionFilePaths);
    }

    /**
     * Build the full system prompt.
     *
     * @param tools the tools available for this turn
     * @return the assembled system prompt string
     */
    public String build(List<ToolSchema> tools) {
        StringBuilder sb = new StringBuilder();

        // 1. Base system prompt
        if (basePrompt != null && !basePrompt.isBlank()) {
            sb.append(basePrompt).append("\n\n");
        }

        // 2. Tool descriptions
        if (tools != null && !tools.isEmpty()) {
            sb.append("## Available Tools\n\n");
            for (ToolSchema tool : tools) {
                sb.append("### ").append(tool.name()).append("\n");
                if (!tool.description().isBlank()) {
                    sb.append(tool.description()).append("\n");
                }
                sb.append("Input schema: ").append(tool.inputSchemaJson()).append("\n\n");
            }
        }

        // 3. Instruction file contents (capped at MAX_INSTRUCTION_CHARS total)
        int remainingChars = MAX_INSTRUCTION_CHARS;
        for (String filePath : instructionFilePaths) {
            if (remainingChars <= 0) break;
            Path path = Path.of(filePath);
            if (!Files.exists(path)) continue;
            try {
                String content = Files.readString(path);
                if (content.length() > remainingChars) {
                    content = content.substring(0, remainingChars);
                }
                sb.append("## Instructions from ").append(path.getFileName()).append("\n\n");
                sb.append(content).append("\n\n");
                remainingChars -= content.length();
            } catch (IOException e) {
                // Skip unreadable files silently — log would be appropriate in production
            }
        }

        return sb.toString().stripTrailing();
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private String basePrompt = "";
        private final List<String> instructionFilePaths = new ArrayList<>();

        private Builder() {}

        public Builder basePrompt(String basePrompt) {
            this.basePrompt = basePrompt;
            return this;
        }

        public Builder addInstructionFile(String filePath) {
            this.instructionFilePaths.add(filePath);
            return this;
        }

        public Builder instructionFiles(List<String> filePaths) {
            this.instructionFilePaths.clear();
            this.instructionFilePaths.addAll(filePaths);
            return this;
        }

        public SystemPromptBuilder build() {
            return new SystemPromptBuilder(this);
        }
    }
}
