package com.agentforge.config.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ToolPermission")
class ToolPermissionTest {

    @Test
    @DisplayName("exact match returns true for identical tool name")
    void matches_exactMatch_returnsTrue() {
        var permission = new ToolPermission("file_read", PermissionLevel.READ_ONLY);
        assertThat(permission.matches("file_read")).isTrue();
    }

    @Test
    @DisplayName("exact match returns false for different tool name")
    void matches_exactMatch_returnsFalse() {
        var permission = new ToolPermission("file_read", PermissionLevel.READ_ONLY);
        assertThat(permission.matches("file_write")).isFalse();
    }

    @Test
    @DisplayName("wildcard * matches any tool name")
    void matches_wildcardStar_matchesAny() {
        var permission = new ToolPermission("*", PermissionLevel.READ_ONLY);
        assertThat(permission.matches("anything")).isTrue();
        assertThat(permission.matches("file_read")).isTrue();
        assertThat(permission.matches("bash")).isTrue();
        assertThat(permission.matches("")).isTrue();
    }

    @Test
    @DisplayName("prefix wildcard file_* matches tools starting with file_")
    void matches_prefixWildcard_matchesPrefix() {
        var permission = new ToolPermission("file_*", PermissionLevel.WORKSPACE_WRITE);
        assertThat(permission.matches("file_read")).isTrue();
        assertThat(permission.matches("file_write")).isTrue();
        assertThat(permission.matches("file_edit")).isTrue();
        assertThat(permission.matches("file_")).isTrue();
    }

    @Test
    @DisplayName("prefix wildcard file_* does not match tools not starting with file_")
    void matches_prefixWildcard_doesNotMatchOthers() {
        var permission = new ToolPermission("file_*", PermissionLevel.WORKSPACE_WRITE);
        assertThat(permission.matches("bash")).isFalse();
        assertThat(permission.matches("grep")).isFalse();
        assertThat(permission.matches("not_file_read")).isFalse();
    }

    @Test
    @DisplayName("suffix wildcard *_read matches tools ending with _read")
    void matches_suffixWildcard_matchesSuffix() {
        var permission = new ToolPermission("*_read", PermissionLevel.READ_ONLY);
        assertThat(permission.matches("file_read")).isTrue();
        assertThat(permission.matches("dir_read")).isTrue();
        assertThat(permission.matches("_read")).isTrue();
    }

    @Test
    @DisplayName("suffix wildcard *_read does not match tools not ending with _read")
    void matches_suffixWildcard_doesNotMatchOthers() {
        var permission = new ToolPermission("*_read", PermissionLevel.READ_ONLY);
        assertThat(permission.matches("file_write")).isFalse();
        assertThat(permission.matches("read_file")).isFalse();
    }

    @Test
    @DisplayName("null toolPattern returns false")
    void matches_nullPattern_returnsFalse() {
        var permission = new ToolPermission(null, PermissionLevel.READ_ONLY);
        assertThat(permission.matches("anything")).isFalse();
    }

    @Test
    @DisplayName("null toolName returns false")
    void matches_nullToolName_returnsFalse() {
        var permission = new ToolPermission("file_read", PermissionLevel.READ_ONLY);
        assertThat(permission.matches(null)).isFalse();
    }

    @Test
    @DisplayName("both null returns false")
    void matches_bothNull_returnsFalse() {
        var permission = new ToolPermission(null, PermissionLevel.READ_ONLY);
        assertThat(permission.matches(null)).isFalse();
    }

    @Test
    @DisplayName("record accessors return correct values")
    void record_accessors() {
        var permission = new ToolPermission("bash", PermissionLevel.DANGER_FULL_ACCESS);
        assertThat(permission.toolPattern()).isEqualTo("bash");
        assertThat(permission.required()).isEqualTo(PermissionLevel.DANGER_FULL_ACCESS);
    }

    @ParameterizedTest
    @DisplayName("pattern matching edge cases")
    @CsvSource({
        "file_*, file_read, true",
        "file_*, file_, true",
        "file_*, file, false",
        "*_write, file_write, true",
        "*_write, write, false",
        "bash, bash, true",
        "bash, Bash, false",
        "*, '', true"
    })
    void matches_parameterized(String pattern, String toolName, boolean expected) {
        var permission = new ToolPermission(pattern, PermissionLevel.READ_ONLY);
        // empty string CSV value comes as empty string, not null
        assertThat(permission.matches(toolName)).isEqualTo(expected);
    }

    @Test
    @DisplayName("two ToolPermissions with same values are equal")
    void equals_sameValues_areEqual() {
        var p1 = new ToolPermission("file_read", PermissionLevel.READ_ONLY);
        var p2 = new ToolPermission("file_read", PermissionLevel.READ_ONLY);
        assertThat(p1).isEqualTo(p2);
        assertThat(p1.hashCode()).isEqualTo(p2.hashCode());
    }

    @Test
    @DisplayName("two ToolPermissions with different values are not equal")
    void equals_differentValues_notEqual() {
        var p1 = new ToolPermission("file_read", PermissionLevel.READ_ONLY);
        var p2 = new ToolPermission("file_write", PermissionLevel.WORKSPACE_WRITE);
        assertThat(p1).isNotEqualTo(p2);
    }
}
