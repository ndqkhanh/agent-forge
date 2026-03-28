package agentforge.events;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory event bus for testing — simulates Kafka topic semantics.
 */
public final class InMemoryEventBus implements EventBus {

    /** topic → ordered list of committed events. */
    private final Map<String, List<Event>> topics = new ConcurrentHashMap<>();

    /** Set of published event IDs for deduplication. */
    private final Set<String> publishedIds = ConcurrentHashMap.newKeySet();

    @Override
    public void publish(List<Event> events) {
        for (Event event : events) {
            if (publishedIds.add(event.id())) {
                topics.computeIfAbsent(event.topic(), k -> new CopyOnWriteArrayList<>())
                        .add(event);
            }
        }
    }

    @Override
    public List<Event> read(String topic, long fromOffset, int maxCount) {
        List<Event> topicEvents = topics.getOrDefault(topic, List.of());
        int from = (int) Math.min(fromOffset, topicEvents.size());
        int to = (int) Math.min(from + maxCount, topicEvents.size());
        if (from >= to) return List.of();
        return List.copyOf(topicEvents.subList(from, to));
    }

    @Override
    public boolean isDuplicate(String eventId) {
        return publishedIds.contains(eventId);
    }
}
