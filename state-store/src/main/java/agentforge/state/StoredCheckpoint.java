package agentforge.state;

import java.time.Instant;
import java.util.Map;

/**
 * A checkpoint persisted in the store.
 *
 * @param id         unique checkpoint ID
 * @param workflowId the workflow this belongs to
 * @param data       task results snapshot (taskId → output)
 * @param version    optimistic locking version (incremented on update)
 * @param createdAt  creation timestamp
 * @param expiresAt  expiry time (null = no expiry)
 */
public record StoredCheckpoint(
        String id,
        String workflowId,
        Map<String, String> data,
        long version,
        Instant createdAt,
        Instant expiresAt) {

    public StoredCheckpoint {
        data = Map.copyOf(data);
    }

    public boolean isExpired() {
        return expiresAt != null && Instant.now().isAfter(expiresAt);
    }
}
