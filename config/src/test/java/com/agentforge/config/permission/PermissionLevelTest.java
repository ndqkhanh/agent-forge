package com.agentforge.config.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("PermissionLevel")
class PermissionLevelTest {

    @Test
    @DisplayName("READ_ONLY allows READ_ONLY")
    void readOnly_allowsReadOnly() {
        assertThat(PermissionLevel.READ_ONLY.allows(PermissionLevel.READ_ONLY)).isTrue();
    }

    @Test
    @DisplayName("READ_ONLY does not allow WORKSPACE_WRITE")
    void readOnly_doesNotAllowWorkspaceWrite() {
        assertThat(PermissionLevel.READ_ONLY.allows(PermissionLevel.WORKSPACE_WRITE)).isFalse();
    }

    @Test
    @DisplayName("READ_ONLY does not allow DANGER_FULL_ACCESS")
    void readOnly_doesNotAllowDangerFullAccess() {
        assertThat(PermissionLevel.READ_ONLY.allows(PermissionLevel.DANGER_FULL_ACCESS)).isFalse();
    }

    @Test
    @DisplayName("WORKSPACE_WRITE allows READ_ONLY")
    void workspaceWrite_allowsReadOnly() {
        assertThat(PermissionLevel.WORKSPACE_WRITE.allows(PermissionLevel.READ_ONLY)).isTrue();
    }

    @Test
    @DisplayName("WORKSPACE_WRITE allows WORKSPACE_WRITE")
    void workspaceWrite_allowsWorkspaceWrite() {
        assertThat(PermissionLevel.WORKSPACE_WRITE.allows(PermissionLevel.WORKSPACE_WRITE)).isTrue();
    }

    @Test
    @DisplayName("WORKSPACE_WRITE does not allow DANGER_FULL_ACCESS")
    void workspaceWrite_doesNotAllowDangerFullAccess() {
        assertThat(PermissionLevel.WORKSPACE_WRITE.allows(PermissionLevel.DANGER_FULL_ACCESS)).isFalse();
    }

    @Test
    @DisplayName("DANGER_FULL_ACCESS allows all levels")
    void dangerFullAccess_allowsAll() {
        assertThat(PermissionLevel.DANGER_FULL_ACCESS.allows(PermissionLevel.READ_ONLY)).isTrue();
        assertThat(PermissionLevel.DANGER_FULL_ACCESS.allows(PermissionLevel.WORKSPACE_WRITE)).isTrue();
        assertThat(PermissionLevel.DANGER_FULL_ACCESS.allows(PermissionLevel.DANGER_FULL_ACCESS)).isTrue();
    }

    @ParameterizedTest
    @DisplayName("allows is transitive: higher level allows lower")
    @CsvSource({
        "DANGER_FULL_ACCESS, READ_ONLY, true",
        "DANGER_FULL_ACCESS, WORKSPACE_WRITE, true",
        "WORKSPACE_WRITE, READ_ONLY, true",
        "READ_ONLY, WORKSPACE_WRITE, false",
        "READ_ONLY, DANGER_FULL_ACCESS, false",
        "WORKSPACE_WRITE, DANGER_FULL_ACCESS, false"
    })
    void allows_transitivity(String current, String required, boolean expected) {
        PermissionLevel currentLevel = PermissionLevel.valueOf(current);
        PermissionLevel requiredLevel = PermissionLevel.valueOf(required);
        assertThat(currentLevel.allows(requiredLevel)).isEqualTo(expected);
    }

    @Test
    @DisplayName("valueOf works for all enum constants")
    void valueOf_allConstants() {
        assertThat(PermissionLevel.valueOf("READ_ONLY")).isEqualTo(PermissionLevel.READ_ONLY);
        assertThat(PermissionLevel.valueOf("WORKSPACE_WRITE")).isEqualTo(PermissionLevel.WORKSPACE_WRITE);
        assertThat(PermissionLevel.valueOf("DANGER_FULL_ACCESS")).isEqualTo(PermissionLevel.DANGER_FULL_ACCESS);
    }

    @Test
    @DisplayName("values() returns all three levels")
    void values_containsAllLevels() {
        assertThat(PermissionLevel.values()).containsExactly(
            PermissionLevel.READ_ONLY,
            PermissionLevel.WORKSPACE_WRITE,
            PermissionLevel.DANGER_FULL_ACCESS
        );
    }
}
