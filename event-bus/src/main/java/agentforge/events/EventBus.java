package agentforge.events;

import java.util.List;

/**
 * Transport abstraction for the event bus.
 * Real implementation would use Kafka; in-memory for unit testing.
 */
public interface EventBus {

    /** Publish committed events to a topic. */
    void publish(List<Event> events);

    /** Read events from a topic starting at the given offset. */
    List<Event> read(String topic, long fromOffset, int maxCount);

    /** Check if an event ID has already been published (for deduplication). */
    boolean isDuplicate(String eventId);
}
