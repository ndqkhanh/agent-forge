package agentforge.state;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Abstraction for checkpoint persistence.
 * Real implementation uses Redis WATCH/MULTI/EXEC for optimistic concurrency.
 * In-memory implementation for unit testing.
 */
public interface CheckpointStore {

    /** Save a checkpoint, returning its ID. */
    String save(String workflowId, Map<String, String> data);

    /** Save a checkpoint with a TTL in milliseconds. */
    String saveWithTtl(String workflowId, Map<String, String> data, long ttlMs);

    /** Load a checkpoint by ID (returns empty if not found or expired). */
    Optional<StoredCheckpoint> load(String checkpointId);

    /** Delete a checkpoint. */
    void delete(String checkpointId);

    /** List checkpoint IDs for a workflow. */
    List<String> listByWorkflow(String workflowId);

    /**
     * Compare-and-swap update — only succeeds if the current version matches.
     * Mirrors Redis WATCH/MULTI/EXEC optimistic concurrency.
     *
     * @param checkpointId the checkpoint to update
     * @param expectedVersion the version the caller last read
     * @param newData the new data to set
     * @return true if update succeeded, false if version conflict
     */
    boolean compareAndSwap(String checkpointId, long expectedVersion, Map<String, String> newData);

    /** Total number of active (non-expired) checkpoints. */
    int count();
}
