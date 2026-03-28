package agentforge.events;

import java.time.Instant;
import java.util.UUID;

/**
 * An event in the AgentForge event bus.
 *
 * @param id        unique event ID
 * @param topic     the topic this event belongs to
 * @param key       partition key for ordering
 * @param payload   the event payload (serialized JSON)
 * @param timestamp when the event was created
 */
public record Event(
        String id,
        String topic,
        String key,
        String payload,
        Instant timestamp) {

    public static Event create(String topic, String key, String payload) {
        return new Event(UUID.randomUUID().toString(), topic, key, payload, Instant.now());
    }
}
