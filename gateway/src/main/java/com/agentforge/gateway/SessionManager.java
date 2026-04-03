package com.agentforge.gateway;

import com.agentforge.common.model.Session;
import com.agentforge.runtime.ConversationRuntime;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Manages active sessions. Each entry holds a ConversationRuntime, Session state,
 * and creation metadata. Thread-safe via ConcurrentHashMap.
 */
public final class SessionManager {

    /** Immutable snapshot of a managed session's public state. */
    public record SessionEntry(
        String id,
        ConversationRuntime runtime,
        Instant createdAt,
        String model
    ) {
        public SessionEntry {
            if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
            if (runtime == null) throw new IllegalArgumentException("runtime must not be null");
            if (createdAt == null) throw new IllegalArgumentException("createdAt must not be null");
            if (model == null || model.isBlank()) throw new IllegalArgumentException("model must not be blank");
        }

        public Session session() {
            return runtime.getSession();
        }
    }

    private final ConcurrentHashMap<String, SessionEntry> sessions = new ConcurrentHashMap<>();

    /**
     * Register a new session. Overwrites any existing session with the same id.
     *
     * @param id      session id
     * @param runtime the runtime backing this session
     * @param model   model name associated with the session
     * @return the created SessionEntry
     */
    public SessionEntry create(String id, ConversationRuntime runtime, String model) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id must not be blank");
        if (runtime == null) throw new IllegalArgumentException("runtime must not be null");
        if (model == null || model.isBlank()) throw new IllegalArgumentException("model must not be blank");

        SessionEntry entry = new SessionEntry(id, runtime, Instant.now(), model);
        sessions.put(id, entry);
        return entry;
    }

    /**
     * Look up a session by id.
     *
     * @param id the session id
     * @return an Optional containing the SessionEntry, or empty if not found
     */
    public Optional<SessionEntry> get(String id) {
        if (id == null) return Optional.empty();
        return Optional.ofNullable(sessions.get(id));
    }

    /**
     * Remove a session by id.
     *
     * @param id the session id
     * @return true if a session was removed, false if not found
     */
    public boolean remove(String id) {
        if (id == null) return false;
        return sessions.remove(id) != null;
    }

    /**
     * @return number of active sessions
     */
    public int size() {
        return sessions.size();
    }

    /**
     * @return unmodifiable snapshot of all active session ids
     */
    public Set<String> sessionIds() {
        return Set.copyOf(sessions.keySet());
    }

    /**
     * @return true if a session with the given id exists
     */
    public boolean contains(String id) {
        if (id == null) return false;
        return sessions.containsKey(id);
    }
}
