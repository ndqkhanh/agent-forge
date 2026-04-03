package com.agentforge.runtime.session;

import com.agentforge.common.json.SessionSerializer;
import com.agentforge.common.model.Session;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JSON file-based session persistence. Each session is stored as
 * {directory}/{sessionId}.json. Thread-safe for concurrent save/load.
 */
public final class FileSessionStore implements SessionStore {

    private final Path directory;

    public FileSessionStore(Path directory) {
        if (directory == null) throw new IllegalArgumentException("directory must not be null");
        try {
            Files.createDirectories(directory);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create session directory: " + directory, e);
        }
        this.directory = directory;
    }

    @Override
    public synchronized void save(Session session) {
        if (session == null) throw new IllegalArgumentException("session must not be null");
        Path file = sessionFile(session.id());
        String json = SessionSerializer.serialize(session);
        try {
            Files.writeString(file, json);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save session: " + session.id(), e);
        }
    }

    @Override
    public synchronized Optional<Session> load(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return Optional.empty();
        Path file = sessionFile(sessionId);
        if (!Files.exists(file)) return Optional.empty();
        try {
            String json = Files.readString(file);
            return Optional.of(SessionSerializer.deserialize(json));
        } catch (IOException e) {
            throw new RuntimeException("Failed to load session: " + sessionId, e);
        }
    }

    @Override
    public synchronized List<String> listSessionIds() {
        try (var stream = Files.list(directory)) {
            List<String> ids = new ArrayList<>();
            stream.filter(p -> p.toString().endsWith(".json"))
                  .forEach(p -> {
                      String filename = p.getFileName().toString();
                      ids.add(filename.substring(0, filename.length() - 5));
                  });
            return List.copyOf(ids);
        } catch (IOException e) {
            throw new RuntimeException("Failed to list sessions in: " + directory, e);
        }
    }

    @Override
    public synchronized void delete(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) return;
        Path file = sessionFile(sessionId);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            throw new RuntimeException("Failed to delete session: " + sessionId, e);
        }
    }

    private Path sessionFile(String sessionId) {
        return directory.resolve(sessionId + ".json");
    }
}
