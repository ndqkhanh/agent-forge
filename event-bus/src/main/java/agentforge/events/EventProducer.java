package agentforge.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Transactional event producer — mirrors the Kafka Producer transactional API:
 *
 * 1. initTransactions()   — initialize transactional state
 * 2. beginTransaction()   — start a new transaction
 * 3. send(event)          — buffer event within transaction
 * 4. commitTransaction()  — atomically publish all buffered events
 *    OR abortTransaction() — discard all buffered events
 *
 * Demonstrates exactly-once producer semantics via idempotent deduplication.
 */
public final class EventProducer {

    private static final Logger log = LoggerFactory.getLogger(EventProducer.class);

    private final String producerId;
    private final EventBus eventBus;

    private boolean initialized = false;
    private boolean inTransaction = false;
    private List<Event> transactionBuffer = new ArrayList<>();
    private final AtomicLong committedCount = new AtomicLong(0);

    public EventProducer(String producerId, EventBus eventBus) {
        this.producerId = producerId;
        this.eventBus = eventBus;
    }

    /**
     * Initialize transactional state. Must be called before beginTransaction().
     * Mirrors: KafkaProducer.initTransactions()
     */
    public void initTransactions() {
        initialized = true;
        log.info("Producer {} initialized transactions", producerId);
    }

    /**
     * Begin a new transaction. Events sent after this call are buffered
     * until commitTransaction() or abortTransaction().
     * Mirrors: KafkaProducer.beginTransaction()
     */
    public void beginTransaction() {
        if (!initialized) {
            throw new IllegalStateException("Must call initTransactions() before beginTransaction()");
        }
        if (inTransaction) {
            throw new IllegalStateException("Transaction already active for producer " + producerId);
        }
        inTransaction = true;
        transactionBuffer = new ArrayList<>();
        log.debug("Producer {} began transaction", producerId);
    }

    /**
     * Send an event within the current transaction (buffered, not published yet).
     * Mirrors: KafkaProducer.send()
     */
    public void send(Event event) {
        if (inTransaction) {
            // Dedup within transaction by event ID
            if (transactionBuffer.stream().noneMatch(e -> e.id().equals(event.id()))
                    && !eventBus.isDuplicate(event.id())) {
                transactionBuffer.add(event);
            }
        } else {
            throw new IllegalStateException("Must call beginTransaction() before send()");
        }
    }

    /**
     * Send an event directly (non-transactional, immediately published).
     * Used for events that don't need transactional guarantees.
     */
    public void sendDirect(Event event) {
        if (!initialized) {
            throw new IllegalStateException("Must call initTransactions() first");
        }
        eventBus.publish(List.of(event));
        committedCount.incrementAndGet();
    }

    /**
     * Commit the current transaction — atomically publish all buffered events.
     * Mirrors: KafkaProducer.commitTransaction()
     */
    public void commitTransaction() {
        if (!inTransaction) {
            throw new IllegalStateException("No active transaction to commit");
        }
        eventBus.publish(transactionBuffer);
        committedCount.addAndGet(transactionBuffer.size());
        log.info("Producer {} committed transaction ({} events)", producerId, transactionBuffer.size());
        transactionBuffer = new ArrayList<>();
        inTransaction = false;
    }

    /**
     * Abort the current transaction — discard all buffered events.
     * Mirrors: KafkaProducer.abortTransaction()
     */
    public void abortTransaction() {
        if (!inTransaction) {
            throw new IllegalStateException("No active transaction to abort");
        }
        log.info("Producer {} aborted transaction ({} events discarded)",
                producerId, transactionBuffer.size());
        transactionBuffer = new ArrayList<>();
        inTransaction = false;
    }

    /**
     * Total number of committed events across all transactions.
     */
    public long committedEventCount() {
        return committedCount.get();
    }
}
