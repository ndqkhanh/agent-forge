package com.agentforge.gateway;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class SlashCommandTest {

    // --- parse: recognized commands ---

    @Test
    void parse_help_returnsHelp() {
        Optional<SlashCommand> cmd = SlashCommand.parse("/help");
        assertThat(cmd).isPresent();
        assertThat(cmd.get()).isInstanceOf(SlashCommand.Help.class);
    }

    @Test
    void parse_quit_returnsQuit() {
        Optional<SlashCommand> cmd = SlashCommand.parse("/quit");
        assertThat(cmd).isPresent();
        assertThat(cmd.get()).isInstanceOf(SlashCommand.Quit.class);
    }

    @Test
    void parse_exit_returnsQuit() {
        Optional<SlashCommand> cmd = SlashCommand.parse("/exit");
        assertThat(cmd).isPresent();
        assertThat(cmd.get()).isInstanceOf(SlashCommand.Quit.class);
    }

    @Test
    void parse_clear_returnsClear() {
        Optional<SlashCommand> cmd = SlashCommand.parse("/clear");
        assertThat(cmd).isPresent();
        assertThat(cmd.get()).isInstanceOf(SlashCommand.Clear.class);
    }

    @Test
    void parse_compact_returnsCompact() {
        Optional<SlashCommand> cmd = SlashCommand.parse("/compact");
        assertThat(cmd).isPresent();
        assertThat(cmd.get()).isInstanceOf(SlashCommand.Compact.class);
    }

    @Test
    void parse_status_returnsStatus() {
        Optional<SlashCommand> cmd = SlashCommand.parse("/status");
        assertThat(cmd).isPresent();
        assertThat(cmd.get()).isInstanceOf(SlashCommand.Status.class);
    }

    @Test
    void parse_modelNoArg_returnsModelWithBlankName() {
        Optional<SlashCommand> cmd = SlashCommand.parse("/model");
        assertThat(cmd).isPresent();
        assertThat(cmd.get()).isInstanceOf(SlashCommand.Model.class);
        assertThat(((SlashCommand.Model) cmd.get()).modelName()).isBlank();
    }

    @Test
    void parse_modelWithArg_returnsModelWithName() {
        Optional<SlashCommand> cmd = SlashCommand.parse("/model claude-sonnet-4-6");
        assertThat(cmd).isPresent();
        SlashCommand.Model m = (SlashCommand.Model) cmd.get();
        assertThat(m.modelName()).isEqualTo("claude-sonnet-4-6");
    }

    // --- parse: non-commands ---

    @Test
    void parse_null_returnsEmpty() {
        assertThat(SlashCommand.parse(null)).isEmpty();
    }

    @Test
    void parse_blank_returnsEmpty() {
        assertThat(SlashCommand.parse("  ")).isEmpty();
    }

    @Test
    void parse_regularText_returnsEmpty() {
        assertThat(SlashCommand.parse("hello world")).isEmpty();
    }

    @Test
    void parse_unknownSlashCommand_returnsEmpty() {
        assertThat(SlashCommand.parse("/unknown")).isEmpty();
    }

    // --- case insensitivity ---

    @Test
    void parse_helpUpperCase_returnsHelp() {
        assertThat(SlashCommand.parse("/HELP")).isPresent()
            .hasValueSatisfying(c -> assertThat(c).isInstanceOf(SlashCommand.Help.class));
    }

    @Test
    void parse_quitMixedCase_returnsQuit() {
        assertThat(SlashCommand.parse("/Quit")).isPresent()
            .hasValueSatisfying(c -> assertThat(c).isInstanceOf(SlashCommand.Quit.class));
    }

    // --- whitespace handling ---

    @Test
    void parse_commandWithLeadingWhitespace_recognized() {
        assertThat(SlashCommand.parse("  /help")).isPresent();
    }

    @Test
    void parse_commandWithTrailingWhitespace_recognized() {
        assertThat(SlashCommand.parse("/help  ")).isPresent();
    }

    // --- exhaustive pattern matching ---

    @Test
    void patternMatch_allVariants_exhaustive() {
        // This test ensures the switch is exhaustive and compiles without warnings
        SlashCommand[] commands = {
            new SlashCommand.Help(),
            new SlashCommand.Quit(),
            new SlashCommand.Clear(),
            new SlashCommand.Compact(),
            new SlashCommand.Status(),
            new SlashCommand.Model("gpt-4")
        };

        for (SlashCommand cmd : commands) {
            String label = switch (cmd) {
                case SlashCommand.Help ignored    -> "help";
                case SlashCommand.Quit ignored    -> "quit";
                case SlashCommand.Clear ignored   -> "clear";
                case SlashCommand.Compact ignored -> "compact";
                case SlashCommand.Status ignored  -> "status";
                case SlashCommand.Model m         -> "model:" + m.modelName();
            };
            assertThat(label).isNotBlank();
        }
    }

    // --- helpText ---

    @Test
    void helpText_containsAllCommands() {
        String text = SlashCommand.helpText();
        assertThat(text).contains("/help");
        assertThat(text).contains("/quit");
        assertThat(text).contains("/clear");
        assertThat(text).contains("/compact");
        assertThat(text).contains("/status");
        assertThat(text).contains("/model");
    }

    // --- Model record ---

    @Test
    void model_nullModelName_defaultsToEmpty() {
        SlashCommand.Model m = new SlashCommand.Model(null);
        assertThat(m.modelName()).isEmpty();
    }

    @Test
    void model_withName_storesName() {
        SlashCommand.Model m = new SlashCommand.Model("claude-haiku");
        assertThat(m.modelName()).isEqualTo("claude-haiku");
    }
}
