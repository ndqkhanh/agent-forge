package com.agentforge.runtime.prompt;

import com.agentforge.common.model.ToolSchema;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SystemPromptBuilderTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("base prompt is included in output")
    void build_basePromptIncluded() {
        SystemPromptBuilder builder = SystemPromptBuilder.builder()
            .basePrompt("You are a helpful assistant.")
            .build();

        String prompt = builder.build(List.of());
        assertThat(prompt).contains("You are a helpful assistant.");
    }

    @Test
    @DisplayName("tool descriptions are appended")
    void build_toolDescriptionsAppended() {
        ToolSchema tool = new ToolSchema("read_file", "Reads a file from disk",
            "{\"type\":\"object\",\"properties\":{\"path\":{\"type\":\"string\"}}}");
        SystemPromptBuilder builder = SystemPromptBuilder.builder()
            .basePrompt("Base prompt.")
            .build();

        String prompt = builder.build(List.of(tool));
        assertThat(prompt).contains("read_file");
        assertThat(prompt).contains("Reads a file from disk");
        assertThat(prompt).contains("Available Tools");
    }

    @Test
    @DisplayName("instruction file contents are appended")
    void build_instructionFilesAppended() throws IOException {
        Path claudeMd = tempDir.resolve("CLAUDE.md");
        Files.writeString(claudeMd, "Always be concise.");

        SystemPromptBuilder builder = SystemPromptBuilder.builder()
            .basePrompt("Base.")
            .addInstructionFile(claudeMd.toString())
            .build();

        String prompt = builder.build(List.of());
        assertThat(prompt).contains("Always be concise.");
    }

    @Test
    @DisplayName("missing instruction file is silently skipped")
    void build_missingInstructionFile_silentlySkipped() {
        SystemPromptBuilder builder = SystemPromptBuilder.builder()
            .basePrompt("Base.")
            .addInstructionFile("/nonexistent/path/CLAUDE.md")
            .build();

        String prompt = builder.build(List.of());
        assertThat(prompt).contains("Base.");
        assertThat(prompt).doesNotContain("nonexistent");
    }

    @Test
    @DisplayName("instruction file content is capped at 12KB total")
    void build_instructionFileCappedAt12KB() throws IOException {
        // Write a file larger than 12KB
        String largeContent = "X".repeat(20_000);
        Path largeFile = tempDir.resolve("large.md");
        Files.writeString(largeFile, largeContent);

        SystemPromptBuilder builder = SystemPromptBuilder.builder()
            .addInstructionFile(largeFile.toString())
            .build();

        String prompt = builder.build(List.of());
        // Total prompt length should be well under 20k of instruction content
        assertThat(prompt.length()).isLessThan(15_000);
    }

    @Test
    @DisplayName("empty tool list produces no tool section")
    void build_emptyToolList_noToolSection() {
        SystemPromptBuilder builder = SystemPromptBuilder.builder()
            .basePrompt("Base.")
            .build();

        String prompt = builder.build(List.of());
        assertThat(prompt).doesNotContain("Available Tools");
    }

    @Test
    @DisplayName("multiple tools all appear in output")
    void build_multipleTools_allIncluded() {
        ToolSchema tool1 = ToolSchema.noParams("bash", "Execute bash commands");
        ToolSchema tool2 = ToolSchema.noParams("read_file", "Read file contents");

        SystemPromptBuilder builder = SystemPromptBuilder.builder()
            .basePrompt("Base.")
            .build();

        String prompt = builder.build(List.of(tool1, tool2));
        assertThat(prompt).contains("bash");
        assertThat(prompt).contains("read_file");
    }
}
