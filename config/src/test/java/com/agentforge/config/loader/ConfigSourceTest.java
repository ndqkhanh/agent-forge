package com.agentforge.config.loader;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("ConfigSource")
class ConfigSourceTest {

    @Test
    @DisplayName("values() contains all three sources")
    void values_containsAllSources() {
        assertThat(ConfigSource.values()).containsExactly(
            ConfigSource.USER,
            ConfigSource.PROJECT,
            ConfigSource.LOCAL
        );
    }

    @Test
    @DisplayName("valueOf works for USER")
    void valueOf_user() {
        assertThat(ConfigSource.valueOf("USER")).isEqualTo(ConfigSource.USER);
    }

    @Test
    @DisplayName("valueOf works for PROJECT")
    void valueOf_project() {
        assertThat(ConfigSource.valueOf("PROJECT")).isEqualTo(ConfigSource.PROJECT);
    }

    @Test
    @DisplayName("valueOf works for LOCAL")
    void valueOf_local() {
        assertThat(ConfigSource.valueOf("LOCAL")).isEqualTo(ConfigSource.LOCAL);
    }

    @Test
    @DisplayName("enum name matches constant name")
    void name_matchesConstantName() {
        assertThat(ConfigSource.USER.name()).isEqualTo("USER");
        assertThat(ConfigSource.PROJECT.name()).isEqualTo("PROJECT");
        assertThat(ConfigSource.LOCAL.name()).isEqualTo("LOCAL");
    }

    @Test
    @DisplayName("ordinal order is USER=0, PROJECT=1, LOCAL=2")
    void ordinal_order() {
        assertThat(ConfigSource.USER.ordinal()).isEqualTo(0);
        assertThat(ConfigSource.PROJECT.ordinal()).isEqualTo(1);
        assertThat(ConfigSource.LOCAL.ordinal()).isEqualTo(2);
    }

    @Test
    @DisplayName("exhaustive switch covers all cases")
    void exhaustiveSwitch_allCases() {
        for (ConfigSource source : ConfigSource.values()) {
            String label = switch (source) {
                case USER -> "user";
                case PROJECT -> "project";
                case LOCAL -> "local";
            };
            assertThat(label).isNotNull();
        }
    }
}
