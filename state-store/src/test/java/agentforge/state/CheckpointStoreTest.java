package agentforge.state;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD tests for CheckpointStore — persistent checkpoint storage.
 *
 * Models Redis WATCH/MULTI/EXEC optimistic concurrency.
 * Uses in-memory backing for unit tests.
 *
 * Tests cover:
 * - Save and load checkpoint data
 * - Delete checkpoint
 * - List checkpoints by workflow
 * - Optimistic locking (version conflict detection)
 * - TTL expiry
 * - Checkpoint serialization round-trip
 */
class CheckpointStoreTest {

    private CheckpointStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryCheckpointStore();
    }

    // ========== Save & Load ==========

    @Test
    @DisplayName("save and load round-trips correctly")
    void saveAndLoadRoundTrips() {
        Map<String, String> data = Map.of("task-A", "output-A", "task-B", "output-B");
        String cpId = store.save("wf-1", data);

        assertThat(cpId).isNotNull();

        var loaded = store.load(cpId);
        assertThat(loaded).isPresent();
        assertThat(loaded.get().data()).isEqualTo(data);
        assertThat(loaded.get().workflowId()).isEqualTo("wf-1");
    }

    @Test
    @DisplayName("load nonexistent returns empty")
    void loadNonexistentReturnsEmpty() {
        assertThat(store.load("nonexistent")).isEmpty();
    }

    // ========== Delete ==========

    @Test
    @DisplayName("delete removes checkpoint")
    void deleteRemovesCheckpoint() {
        String cpId = store.save("wf-1", Map.of("k", "v"));
        assertThat(store.load(cpId)).isPresent();

        store.delete(cpId);
        assertThat(store.load(cpId)).isEmpty();
    }

    // ========== List by Workflow ==========

    @Test
    @DisplayName("listByWorkflow returns only checkpoints for that workflow")
    void listByWorkflowFilters() {
        store.save("wf-1", Map.of("a", "1"));
        store.save("wf-1", Map.of("b", "2"));
        store.save("wf-2", Map.of("c", "3"));

        assertThat(store.listByWorkflow("wf-1")).hasSize(2);
        assertThat(store.listByWorkflow("wf-2")).hasSize(1);
        assertThat(store.listByWorkflow("wf-3")).isEmpty();
    }

    // ========== Optimistic Locking ==========

    @Test
    @DisplayName("compareAndSwap succeeds with correct version")
    void casSucceedsWithCorrectVersion() {
        String cpId = store.save("wf-1", Map.of("k", "v1"));
        long version = store.load(cpId).get().version();

        boolean success = store.compareAndSwap(cpId, version, Map.of("k", "v2"));

        assertThat(success).isTrue();
        assertThat(store.load(cpId).get().data()).containsEntry("k", "v2");
    }

    @Test
    @DisplayName("compareAndSwap fails with stale version")
    void casFailsWithStaleVersion() {
        String cpId = store.save("wf-1", Map.of("k", "v1"));

        // Update once to advance version
        store.compareAndSwap(cpId, 1, Map.of("k", "v2"));

        // Try with old version
        boolean success = store.compareAndSwap(cpId, 1, Map.of("k", "v3"));
        assertThat(success).isFalse();
        // Data unchanged
        assertThat(store.load(cpId).get().data()).containsEntry("k", "v2");
    }

    // ========== TTL Expiry ==========

    @Test
    @DisplayName("expired checkpoints are not returned")
    void expiredCheckpointsNotReturned() {
        String cpId = store.saveWithTtl("wf-1", Map.of("k", "v"), 0); // 0ms TTL = immediate expiry

        // Allow expiry
        assertThat(store.load(cpId)).isEmpty();
    }

    // ========== Stats ==========

    @Test
    @DisplayName("count tracks active checkpoints")
    void countTracksActive() {
        assertThat(store.count()).isEqualTo(0);

        store.save("wf-1", Map.of());
        store.save("wf-2", Map.of());
        assertThat(store.count()).isEqualTo(2);

        String cpId = store.listByWorkflow("wf-1").getFirst();
        store.delete(cpId);
        assertThat(store.count()).isEqualTo(1);
    }
}
