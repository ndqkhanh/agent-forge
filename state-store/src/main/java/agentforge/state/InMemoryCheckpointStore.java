package agentforge.state;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory checkpoint store — mirrors Redis semantics for unit testing.
 * Supports optimistic locking via version numbers and TTL expiry.
 */
public final class InMemoryCheckpointStore implements CheckpointStore {

    private static final Logger log = LoggerFactory.getLogger(InMemoryCheckpointStore.class);

    private final Map<String, StoredCheckpoint> store = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(1);

    @Override
    public String save(String workflowId, Map<String, String> data) {
        return saveWithTtl(workflowId, data, -1);
    }

    @Override
    public String saveWithTtl(String workflowId, Map<String, String> data, long ttlMs) {
        String cpId = "scp-" + idCounter.getAndIncrement();
        Instant expiresAt = ttlMs >= 0 ? Instant.now().plusMillis(ttlMs) : null;
        var checkpoint = new StoredCheckpoint(cpId, workflowId, data, 1, Instant.now(), expiresAt);
        store.put(cpId, checkpoint);
        log.debug("Saved checkpoint {} for workflow {} (ttl={}ms)", cpId, workflowId, ttlMs);
        return cpId;
    }

    @Override
    public Optional<StoredCheckpoint> load(String checkpointId) {
        StoredCheckpoint cp = store.get(checkpointId);
        if (cp == null) return Optional.empty();
        if (cp.isExpired()) {
            store.remove(checkpointId);
            return Optional.empty();
        }
        return Optional.of(cp);
    }

    @Override
    public void delete(String checkpointId) {
        store.remove(checkpointId);
    }

    @Override
    public List<String> listByWorkflow(String workflowId) {
        return store.values().stream()
                .filter(cp -> cp.workflowId().equals(workflowId) && !cp.isExpired())
                .map(StoredCheckpoint::id)
                .toList();
    }

    @Override
    public boolean compareAndSwap(String checkpointId, long expectedVersion, Map<String, String> newData) {
        StoredCheckpoint current = store.get(checkpointId);
        if (current == null || current.version() != expectedVersion) {
            return false;
        }
        var updated = new StoredCheckpoint(
                current.id(), current.workflowId(), newData,
                current.version() + 1, current.createdAt(), current.expiresAt());
        store.put(checkpointId, updated);
        return true;
    }

    @Override
    public int count() {
        // Clean expired entries first
        store.values().removeIf(StoredCheckpoint::isExpired);
        return store.size();
    }
}
