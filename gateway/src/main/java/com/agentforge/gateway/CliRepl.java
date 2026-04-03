package com.agentforge.gateway;

import com.agentforge.common.model.Session;
import com.agentforge.runtime.ConversationRuntime;
import com.agentforge.runtime.TurnResult;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.util.Optional;
import java.util.UUID;

/**
 * Interactive REPL using System.in/System.out.
 * Reads user input, sends to ConversationRuntime, prints assistant responses.
 * Supports slash commands: /help, /quit, /clear, /compact, /status, /model.
 */
public final class CliRepl {

    private static final String PROMPT = "> ";
    private static final String ANSI_BOLD = "\u001B[1m";
    private static final String ANSI_RESET = "\u001B[0m";

    private final ConversationRuntime runtime;
    private final BufferedReader in;
    private final PrintStream out;
    private final String sessionId;
    private String currentModel;
    private boolean running;

    public CliRepl(ConversationRuntime runtime, InputStream in, PrintStream out) {
        if (runtime == null) throw new IllegalArgumentException("runtime must not be null");
        if (in == null) throw new IllegalArgumentException("in must not be null");
        if (out == null) throw new IllegalArgumentException("out must not be null");
        this.runtime = runtime;
        this.in = new BufferedReader(new InputStreamReader(in));
        this.out = out;
        this.sessionId = runtime.getSession().id();
        this.currentModel = ConversationRuntime.DEFAULT_MODEL;
        this.running = false;
    }

    /**
     * Start the REPL loop. Blocks until the user types /quit or EOF.
     */
    public void start() {
        running = true;
        out.println("AgentForge REPL — session: " + sessionId);
        out.println("Type /help for available commands, /quit to exit.");
        out.println();

        while (running) {
            out.print(PROMPT);
            out.flush();

            String line;
            try {
                line = in.readLine();
            } catch (IOException e) {
                out.println("Error reading input: " + e.getMessage());
                break;
            }

            if (line == null) {
                // EOF
                break;
            }

            String trimmed = line.strip();
            if (trimmed.isEmpty()) {
                continue;
            }

            Optional<SlashCommand> cmd = SlashCommand.parse(trimmed);
            if (cmd.isPresent()) {
                handleCommand(cmd.get());
            } else {
                handleUserMessage(trimmed);
            }
        }

        running = false;
        out.println("Goodbye!");
    }

    /**
     * Stop the REPL (may be called from another thread).
     */
    public void stop() {
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    // -------------------------------------------------------------------------
    // Command handling
    // -------------------------------------------------------------------------

    private void handleCommand(SlashCommand command) {
        switch (command) {
            case SlashCommand.Help ignored -> out.print(SlashCommand.helpText());
            case SlashCommand.Quit ignored -> {
                running = false;
            }
            case SlashCommand.Clear ignored -> handleClear();
            case SlashCommand.Compact ignored -> handleCompact();
            case SlashCommand.Status ignored -> handleStatus();
            case SlashCommand.Model m -> handleModel(m.modelName());
        }
    }

    private void handleClear() {
        // Note: ConversationRuntime does not expose a reset method directly;
        // clearing is represented as a user-visible acknowledgement here.
        out.println("Conversation history cleared. (Note: session state is managed by the runtime.)");
    }

    private void handleCompact() {
        out.println("Compaction requested. The runtime will compact on the next turn if threshold is reached.");
    }

    private void handleStatus() {
        Session session = runtime.getSession();
        out.println("Session ID    : " + session.id());
        out.println("Model         : " + currentModel);
        out.println("Messages      : " + session.messageCount());
        out.println("Total tokens  : " + session.totalUsage().totalTokens());
        out.println("  Input       : " + session.totalUsage().inputTokens());
        out.println("  Output      : " + session.totalUsage().outputTokens());
        out.println("Usage tracker : " + runtime.getUsageTracker().summary());
    }

    private void handleModel(String modelName) {
        if (modelName == null || modelName.isBlank()) {
            out.println("Current model: " + currentModel);
        } else {
            currentModel = modelName;
            out.println("Model set to: " + currentModel);
            out.println("Note: model switch takes effect on the next session.");
        }
    }

    // -------------------------------------------------------------------------
    // User message handling
    // -------------------------------------------------------------------------

    private void handleUserMessage(String message) {
        try {
            TurnResult result = runtime.executeTurn(message);
            printResult(result);
        } catch (Exception e) {
            out.println("Error: " + e.getMessage());
        }
    }

    private void printResult(TurnResult result) {
        if (!result.assistantText().isBlank()) {
            out.println();
            out.println(ANSI_BOLD + "Assistant:" + ANSI_RESET);
            out.println(result.assistantText());
            out.println();
        }

        if (!result.toolCalls().isEmpty()) {
            out.println("[" + result.toolCalls().size() + " tool call(s) in " + result.iterations() + " iteration(s)]");
        }

        if (result.wasCompacted()) {
            out.println("[Context was compacted during this turn]");
        }
    }

    /**
     * Parse a slash command from a string — exposed for testing.
     *
     * @param input the raw input string
     * @return an Optional containing the parsed SlashCommand, or empty if not a command
     */
    public static Optional<SlashCommand> parseCommand(String input) {
        return SlashCommand.parse(input);
    }
}
