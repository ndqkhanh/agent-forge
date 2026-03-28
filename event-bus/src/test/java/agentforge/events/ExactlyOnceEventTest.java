package agentforge.events;

import org.junit.jupiter.api.*;

import java.util.*;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD tests for exactly-once event semantics.
 *
 * Demonstrates the Kafka transactional producer API contract:
 * initTransactions → beginTransaction → send → commitTransaction
 *
 * Uses an in-memory transport for unit testing.
 * Real Kafka integration would use Testcontainers.
 *
 * Tests cover:
 * - Transactional produce: begin → send → commit
 * - Abort discards events in transaction
 * - Consumer reads only committed events
 * - Exactly-once: duplicate sends within transaction are deduplicated
 * - Consumer offset tracking
 * - Multiple topics
 * - Transaction must be initialized before use
 */
class ExactlyOnceEventTest {

    private InMemoryEventBus eventBus;
    private EventProducer producer;
    private EventConsumer consumer;

    @BeforeEach
    void setUp() {
        eventBus = new InMemoryEventBus();
        producer = new EventProducer("producer-1", eventBus);
        consumer = new EventConsumer("consumer-group-1", eventBus);
    }

    // ========== Transactional Produce ==========

    @Test
    @DisplayName("initTransactions must be called before beginTransaction")
    void initTransactionsRequired() {
        assertThatThrownBy(() -> producer.beginTransaction())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("initTransactions");
    }

    @Test
    @DisplayName("committed transaction events are visible to consumer")
    void committedEventsVisible() {
        producer.initTransactions();
        producer.beginTransaction();
        producer.send(Event.create(Topics.TASK_DISPATCHED.topicName(), "task-1", "{\"task\":\"A\"}"));
        producer.send(Event.create(Topics.TASK_DISPATCHED.topicName(), "task-2", "{\"task\":\"B\"}"));
        producer.commitTransaction();

        List<Event> events = consumer.poll(Topics.TASK_DISPATCHED.topicName(), 10);
        assertThat(events).hasSize(2);
        assertThat(events).extracting(Event::key).containsExactly("task-1", "task-2");
    }

    @Test
    @DisplayName("aborted transaction events are NOT visible to consumer")
    void abortedEventsNotVisible() {
        producer.initTransactions();
        producer.beginTransaction();
        producer.send(Event.create(Topics.TASK_DISPATCHED.topicName(), "task-1", "{\"task\":\"A\"}"));
        producer.abortTransaction();

        List<Event> events = consumer.poll(Topics.TASK_DISPATCHED.topicName(), 10);
        assertThat(events).isEmpty();
    }

    @Test
    @DisplayName("events sent outside transaction are immediately visible")
    void nonTransactionalEventsImmediate() {
        producer.initTransactions();
        // No beginTransaction — send directly
        producer.sendDirect(Event.create(Topics.WORKFLOW_SUBMITTED.topicName(), "wf-1", "{}"));

        List<Event> events = consumer.poll(Topics.WORKFLOW_SUBMITTED.topicName(), 10);
        assertThat(events).hasSize(1);
    }

    // ========== Exactly-Once Semantics ==========

    @Test
    @DisplayName("duplicate event IDs within transaction are deduplicated")
    void duplicateEventsDeduped() {
        producer.initTransactions();
        producer.beginTransaction();

        Event event = Event.create(Topics.TASK_COMPLETED.topicName(), "task-1", "{\"result\":\"ok\"}");
        producer.send(event);
        producer.send(event); // same event ID
        producer.commitTransaction();

        List<Event> events = consumer.poll(Topics.TASK_COMPLETED.topicName(), 10);
        assertThat(events).hasSize(1);
    }

    // ========== Consumer Offset Tracking ==========

    @Test
    @DisplayName("consumer tracks offset and doesn't re-read committed events")
    void consumerTracksOffset() {
        producer.initTransactions();

        // First batch
        producer.beginTransaction();
        producer.send(Event.create(Topics.TASK_DISPATCHED.topicName(), "t-1", "{}"));
        producer.commitTransaction();

        List<Event> batch1 = consumer.poll(Topics.TASK_DISPATCHED.topicName(), 10);
        assertThat(batch1).hasSize(1);
        consumer.commitOffset(Topics.TASK_DISPATCHED.topicName());

        // Second batch
        producer.beginTransaction();
        producer.send(Event.create(Topics.TASK_DISPATCHED.topicName(), "t-2", "{}"));
        producer.commitTransaction();

        List<Event> batch2 = consumer.poll(Topics.TASK_DISPATCHED.topicName(), 10);
        assertThat(batch2).hasSize(1);
        assertThat(batch2.getFirst().key()).isEqualTo("t-2");
    }

    // ========== Multiple Topics ==========

    @Test
    @DisplayName("events on different topics are isolated")
    void multipleTopicsIsolated() {
        producer.initTransactions();
        producer.beginTransaction();
        producer.send(Event.create(Topics.TASK_DISPATCHED.topicName(), "t-1", "{}"));
        producer.send(Event.create(Topics.WORKFLOW_COMPLETED.topicName(), "wf-1", "{}"));
        producer.commitTransaction();

        assertThat(consumer.poll(Topics.TASK_DISPATCHED.topicName(), 10)).hasSize(1);
        assertThat(consumer.poll(Topics.WORKFLOW_COMPLETED.topicName(), 10)).hasSize(1);
        assertThat(consumer.poll(Topics.SPECULATION_STARTED.topicName(), 10)).isEmpty();
    }

    // ========== Transaction State ==========

    @Test
    @DisplayName("cannot commit without active transaction")
    void cannotCommitWithoutTransaction() {
        producer.initTransactions();

        assertThatThrownBy(() -> producer.commitTransaction())
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("cannot begin nested transactions")
    void cannotBeginNestedTransactions() {
        producer.initTransactions();
        producer.beginTransaction();

        assertThatThrownBy(() -> producer.beginTransaction())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already active");
    }

    // ========== Producer Stats ==========

    @Test
    @DisplayName("producer tracks total committed events")
    void producerTracksCommittedEvents() {
        producer.initTransactions();
        producer.beginTransaction();
        producer.send(Event.create(Topics.TASK_DISPATCHED.topicName(), "t-1", "{}"));
        producer.send(Event.create(Topics.TASK_DISPATCHED.topicName(), "t-2", "{}"));
        producer.commitTransaction();

        assertThat(producer.committedEventCount()).isEqualTo(2);

        // Aborted events don't count
        producer.beginTransaction();
        producer.send(Event.create(Topics.TASK_DISPATCHED.topicName(), "t-3", "{}"));
        producer.abortTransaction();

        assertThat(producer.committedEventCount()).isEqualTo(2);
    }
}
