package com.agentforge.config.permission;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("PermissionPolicy")
class PermissionPolicyTest {

    @Test
    @DisplayName("isAllowed returns true when current level satisfies required level")
    void isAllowed_sufficientLevel_returnsTrue() {
        var policy = new PermissionPolicy(
            PermissionLevel.WORKSPACE_WRITE,
            List.of(new ToolPermission("file_write", PermissionLevel.WORKSPACE_WRITE))
        );
        assertThat(policy.isAllowed("file_write")).isTrue();
    }

    @Test
    @DisplayName("isAllowed returns false when current level is below required level")
    void isAllowed_insufficientLevel_returnsFalse() {
        var policy = new PermissionPolicy(
            PermissionLevel.READ_ONLY,
            List.of(new ToolPermission("file_write", PermissionLevel.WORKSPACE_WRITE))
        );
        assertThat(policy.isAllowed("file_write")).isFalse();
    }

    @Test
    @DisplayName("requiredLevel returns first matching rule's level")
    void requiredLevel_firstRuleWins() {
        var policy = new PermissionPolicy(
            PermissionLevel.DANGER_FULL_ACCESS,
            List.of(
                new ToolPermission("bash", PermissionLevel.DANGER_FULL_ACCESS),
                new ToolPermission("bash", PermissionLevel.READ_ONLY) // shadowed
            )
        );
        assertThat(policy.requiredLevel("bash")).isEqualTo(PermissionLevel.DANGER_FULL_ACCESS);
    }

    @Test
    @DisplayName("requiredLevel defaults to WORKSPACE_WRITE for unknown tools")
    void requiredLevel_unknownTool_defaultsToWorkspaceWrite() {
        var policy = new PermissionPolicy(
            PermissionLevel.WORKSPACE_WRITE,
            List.of()
        );
        assertThat(policy.requiredLevel("unknown_tool")).isEqualTo(PermissionLevel.WORKSPACE_WRITE);
    }

    @Test
    @DisplayName("isAllowed defaults to WORKSPACE_WRITE for unmatched tool")
    void isAllowed_unmatchedTool_usesDefaultLevel() {
        var policyWrite = new PermissionPolicy(PermissionLevel.WORKSPACE_WRITE, List.of());
        var policyRead = new PermissionPolicy(PermissionLevel.READ_ONLY, List.of());

        assertThat(policyWrite.isAllowed("unknown_tool")).isTrue();
        assertThat(policyRead.isAllowed("unknown_tool")).isFalse();
    }

    @Test
    @DisplayName("defaultPolicy maps file_read to READ_ONLY")
    void defaultPolicy_fileRead_requiresReadOnly() {
        var policy = PermissionPolicy.defaultPolicy(PermissionLevel.READ_ONLY);
        assertThat(policy.requiredLevel("file_read")).isEqualTo(PermissionLevel.READ_ONLY);
        assertThat(policy.isAllowed("file_read")).isTrue();
    }

    @Test
    @DisplayName("defaultPolicy maps grep to READ_ONLY")
    void defaultPolicy_grep_requiresReadOnly() {
        var policy = PermissionPolicy.defaultPolicy(PermissionLevel.READ_ONLY);
        assertThat(policy.requiredLevel("grep")).isEqualTo(PermissionLevel.READ_ONLY);
        assertThat(policy.isAllowed("grep")).isTrue();
    }

    @Test
    @DisplayName("defaultPolicy maps glob to READ_ONLY")
    void defaultPolicy_glob_requiresReadOnly() {
        var policy = PermissionPolicy.defaultPolicy(PermissionLevel.READ_ONLY);
        assertThat(policy.requiredLevel("glob")).isEqualTo(PermissionLevel.READ_ONLY);
    }

    @Test
    @DisplayName("defaultPolicy maps file_write to WORKSPACE_WRITE")
    void defaultPolicy_fileWrite_requiresWorkspaceWrite() {
        var policy = PermissionPolicy.defaultPolicy(PermissionLevel.WORKSPACE_WRITE);
        assertThat(policy.requiredLevel("file_write")).isEqualTo(PermissionLevel.WORKSPACE_WRITE);
        assertThat(policy.isAllowed("file_write")).isTrue();
    }

    @Test
    @DisplayName("defaultPolicy maps file_edit to WORKSPACE_WRITE")
    void defaultPolicy_fileEdit_requiresWorkspaceWrite() {
        var policy = PermissionPolicy.defaultPolicy(PermissionLevel.WORKSPACE_WRITE);
        assertThat(policy.requiredLevel("file_edit")).isEqualTo(PermissionLevel.WORKSPACE_WRITE);
    }

    @Test
    @DisplayName("defaultPolicy maps bash to DANGER_FULL_ACCESS")
    void defaultPolicy_bash_requiresDangerFullAccess() {
        var policy = PermissionPolicy.defaultPolicy(PermissionLevel.DANGER_FULL_ACCESS);
        assertThat(policy.requiredLevel("bash")).isEqualTo(PermissionLevel.DANGER_FULL_ACCESS);
        assertThat(policy.isAllowed("bash")).isTrue();
    }

    @Test
    @DisplayName("defaultPolicy READ_ONLY cannot use bash")
    void defaultPolicy_readOnly_cannotUseBash() {
        var policy = PermissionPolicy.defaultPolicy(PermissionLevel.READ_ONLY);
        assertThat(policy.isAllowed("bash")).isFalse();
    }

    @Test
    @DisplayName("defaultPolicy READ_ONLY cannot use file_write")
    void defaultPolicy_readOnly_cannotUseFileWrite() {
        var policy = PermissionPolicy.defaultPolicy(PermissionLevel.READ_ONLY);
        assertThat(policy.isAllowed("file_write")).isFalse();
    }

    @Test
    @DisplayName("defaultPolicy WORKSPACE_WRITE cannot use bash")
    void defaultPolicy_workspaceWrite_cannotUseBash() {
        var policy = PermissionPolicy.defaultPolicy(PermissionLevel.WORKSPACE_WRITE);
        assertThat(policy.isAllowed("bash")).isFalse();
    }

    @Test
    @DisplayName("rules list is defensively copied — external mutation does not affect policy")
    void constructor_defensivelyCopiesRules() {
        var mutableRules = new java.util.ArrayList<ToolPermission>();
        mutableRules.add(new ToolPermission("file_read", PermissionLevel.READ_ONLY));
        var policy = new PermissionPolicy(PermissionLevel.READ_ONLY, mutableRules);

        // mutate after construction
        mutableRules.add(new ToolPermission("bash", PermissionLevel.DANGER_FULL_ACCESS));

        // policy should still use original rules — bash should fall through to default
        assertThat(policy.requiredLevel("bash")).isEqualTo(PermissionLevel.WORKSPACE_WRITE);
    }

    @Test
    @DisplayName("wildcard rule matches all tools")
    void wildcardRule_matchesAllTools() {
        var policy = new PermissionPolicy(
            PermissionLevel.DANGER_FULL_ACCESS,
            List.of(new ToolPermission("*", PermissionLevel.DANGER_FULL_ACCESS))
        );
        assertThat(policy.requiredLevel("anything")).isEqualTo(PermissionLevel.DANGER_FULL_ACCESS);
        assertThat(policy.requiredLevel("file_read")).isEqualTo(PermissionLevel.DANGER_FULL_ACCESS);
    }
}
