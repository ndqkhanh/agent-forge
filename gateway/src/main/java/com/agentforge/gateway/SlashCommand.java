package com.agentforge.gateway;

import java.util.Optional;

/**
 * Sealed interface for slash commands supported by the REPL.
 * Use {@link #parse(String)} to get an instance, then pattern-match on subtypes.
 */
public sealed interface SlashCommand
    permits SlashCommand.Help,
            SlashCommand.Quit,
            SlashCommand.Clear,
            SlashCommand.Compact,
            SlashCommand.Status,
            SlashCommand.Model {

    /** Display help information. */
    record Help() implements SlashCommand {}

    /** Quit the REPL. */
    record Quit() implements SlashCommand {}

    /** Clear conversation history in the current session. */
    record Clear() implements SlashCommand {}

    /** Trigger manual compaction of the conversation context. */
    record Compact() implements SlashCommand {}

    /** Show current session status (model, message count, token usage). */
    record Status() implements SlashCommand {}

    /**
     * Switch to a different model.
     *
     * @param modelName the model to switch to; blank means show current model
     */
    record Model(String modelName) implements SlashCommand {
        public Model {
            if (modelName == null) modelName = "";
        }
    }

    /**
     * Parse a raw input line into a SlashCommand.
     *
     * @param input the raw input line (may or may not start with '/')
     * @return an Optional containing the parsed command, or empty if not a slash command
     */
    static Optional<SlashCommand> parse(String input) {
        if (input == null || input.isBlank()) return Optional.empty();
        String trimmed = input.strip();
        if (!trimmed.startsWith("/")) return Optional.empty();

        // Split command and optional argument
        int spaceIdx = trimmed.indexOf(' ');
        String cmd = spaceIdx == -1 ? trimmed : trimmed.substring(0, spaceIdx);
        String arg = spaceIdx == -1 ? "" : trimmed.substring(spaceIdx + 1).strip();

        return Optional.ofNullable(switch (cmd.toLowerCase()) {
            case "/help"    -> new Help();
            case "/quit", "/exit" -> new Quit();
            case "/clear"   -> new Clear();
            case "/compact" -> new Compact();
            case "/status"  -> new Status();
            case "/model"   -> new Model(arg);
            default         -> null;
        });
    }

    /**
     * Returns the help text describing all available slash commands.
     */
    static String helpText() {
        return """
            Available commands:
              /help      — Show this help message
              /quit      — Exit the REPL
              /clear     — Clear conversation history
              /compact   — Compact conversation context
              /status    — Show session status
              /model [name] — Show or switch model
            """;
    }
}
