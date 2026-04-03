package com.agentforge.hooks;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class HookDefinitionTest {

    private HookDefinition hook(List<String> patterns) {
        return new HookDefinition("test", HookType.PRE_TOOL_USE, "echo hi", patterns);
    }

    @Test
    @DisplayName("exact tool name matches")
    void exactMatch_returnsTrue() {
        assertThat(hook(List.of("bash")).matchesTool("bash")).isTrue();
    }

    @Test
    @DisplayName("exact tool name does not match different tool")
    void exactMatch_differentTool_returnsFalse() {
        assertThat(hook(List.of("bash")).matchesTool("python")).isFalse();
    }

    @Test
    @DisplayName("wildcard * matches any tool name")
    void wildcardStar_matchesAnyTool() {
        HookDefinition h = hook(List.of("*"));
        assertThat(h.matchesTool("bash")).isTrue();
        assertThat(h.matchesTool("file_read")).isTrue();
        assertThat(h.matchesTool("anything")).isTrue();
    }

    @Test
    @DisplayName("prefix wildcard file_* matches file_read and file_write")
    void prefixWildcard_matchesToolsWithPrefix() {
        HookDefinition h = hook(List.of("file_*"));
        assertThat(h.matchesTool("file_read")).isTrue();
        assertThat(h.matchesTool("file_write")).isTrue();
    }

    @Test
    @DisplayName("prefix wildcard file_* does not match bash")
    void prefixWildcard_noMatchOnDifferentTool() {
        assertThat(hook(List.of("file_*")).matchesTool("bash")).isFalse();
    }

    @Test
    @DisplayName("multiple patterns — match succeeds if any pattern matches")
    void multiplePatterns_anyMatch_returnsTrue() {
        HookDefinition h = hook(List.of("bash", "python"));
        assertThat(h.matchesTool("bash")).isTrue();
        assertThat(h.matchesTool("python")).isTrue();
    }

    @Test
    @DisplayName("multiple patterns — no match returns false")
    void multiplePatterns_noMatch_returnsFalse() {
        HookDefinition h = hook(List.of("bash", "python"));
        assertThat(h.matchesTool("ruby")).isFalse();
    }

    @Test
    @DisplayName("empty patterns list matches nothing")
    void emptyPatterns_matchesNothing() {
        assertThat(hook(List.of()).matchesTool("bash")).isFalse();
    }

    @Test
    @DisplayName("null tool name does not match")
    void nullToolName_returnsFalse() {
        assertThat(hook(List.of("*")).matchesTool(null)).isFalse();
    }

    @Test
    @DisplayName("toolPatterns list is immutable")
    void toolPatterns_isImmutable() {
        HookDefinition h = hook(List.of("bash"));
        assertThatThrownBy(() -> h.toolPatterns().add("python"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    @DisplayName("suffix wildcard *_tool matches read_tool and write_tool")
    void suffixWildcard_matchesToolsWithSuffix() {
        HookDefinition h = hook(List.of("*_tool"));
        assertThat(h.matchesTool("read_tool")).isTrue();
        assertThat(h.matchesTool("write_tool")).isTrue();
        assertThat(h.matchesTool("read_other")).isFalse();
    }
}
