package com.agentforge.common.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.*;

class RoleTest {

    @Test
    @DisplayName("wireValue returns lowercase name")
    void wireValue() {
        assertThat(Role.USER.wireValue()).isEqualTo("user");
        assertThat(Role.ASSISTANT.wireValue()).isEqualTo("assistant");
        assertThat(Role.SYSTEM.wireValue()).isEqualTo("system");
        assertThat(Role.TOOL.wireValue()).isEqualTo("tool");
    }

    @Test
    @DisplayName("fromWire parses case-insensitively")
    void fromWire() {
        assertThat(Role.fromWire("user")).isEqualTo(Role.USER);
        assertThat(Role.fromWire("ASSISTANT")).isEqualTo(Role.ASSISTANT);
    }

    @ParameterizedTest
    @EnumSource(Role.class)
    @DisplayName("Round-trip: wireValue -> fromWire")
    void roundTrip(Role role) {
        assertThat(Role.fromWire(role.wireValue())).isEqualTo(role);
    }

    @Test
    @DisplayName("fromWire rejects unknown values")
    void fromWireRejectsUnknown() {
        assertThatThrownBy(() -> Role.fromWire("unknown"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
