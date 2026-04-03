package com.agentforge.runtime.session;

import com.agentforge.common.model.Session;

import java.util.List;
import java.util.Optional;

/**
 * Persistence abstraction for conversation sessions.
 */
public interface SessionStore {
    void save(Session session);
    Optional<Session> load(String sessionId);
    List<String> listSessionIds();
    void delete(String sessionId);
}
