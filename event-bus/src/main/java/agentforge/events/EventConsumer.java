package agentforge.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Event consumer with offset tracking — reads only committed events.
 *
 * Mirrors Kafka consumer semantics: tracks per-topic offsets,
 * only reads events committed after the last consumed offset.
 * Supports explicit offset commit for exactly-once consumption.
 */
public final class EventConsumer {

    private static final Logger log = LoggerFactory.getLogger(EventConsumer.class);

    private final String groupId;
    private final EventBus eventBus;

    /** Per-topic committed offset (next offset to read from). */
    private final Map<String, Long> committedOffsets = new ConcurrentHashMap<>();

    /** Per-topic current read offset (advances on poll, committed on commitOffset). */
    private final Map<String, Long> currentOffsets = new ConcurrentHashMap<>();

    public EventConsumer(String groupId, EventBus eventBus) {
        this.groupId = groupId;
        this.eventBus = eventBus;
    }

    /**
     * Poll for new events on a topic since the last committed offset.
     *
     * @param topic    the topic to read from
     * @param maxCount maximum number of events to return
     * @return list of new events
     */
    public List<Event> poll(String topic, int maxCount) {
        long fromOffset = committedOffsets.getOrDefault(topic, 0L);
        List<Event> events = eventBus.read(topic, fromOffset, maxCount);

        if (!events.isEmpty()) {
            currentOffsets.put(topic, fromOffset + events.size());
            log.debug("Consumer {} polled {} events from {} (offset {}→{})",
                    groupId, events.size(), topic, fromOffset, fromOffset + events.size());
        }

        return events;
    }

    /**
     * Commit the current offset for a topic.
     * After commit, subsequent polls will not re-read these events.
     * Mirrors: KafkaConsumer.commitSync() / sendOffsetsToTransaction()
     */
    public void commitOffset(String topic) {
        Long current = currentOffsets.get(topic);
        if (current != null) {
            committedOffsets.put(topic, current);
            log.debug("Consumer {} committed offset {} for {}",
                    groupId, current, topic);
        }
    }

    /**
     * Get the current committed offset for a topic.
     */
    public long committedOffset(String topic) {
        return committedOffsets.getOrDefault(topic, 0L);
    }
}
