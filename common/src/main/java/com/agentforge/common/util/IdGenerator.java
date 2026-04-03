package com.agentforge.common.util;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;

/**
 * Generates unique IDs with type prefixes (e.g., "msg_01J3KX...").
 */
public final class IdGenerator {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();

    private IdGenerator() {}

    public static String generate(String prefix) {
        var bytes = new byte[16];
        RANDOM.nextBytes(bytes);
        // Embed timestamp in first 6 bytes for sortability
        long millis = Instant.now().toEpochMilli();
        for (int i = 5; i >= 0; i--) {
            bytes[i] = (byte) (millis & 0xFF);
            millis >>= 8;
        }
        return prefix + "_" + ENCODER.encodeToString(bytes);
    }

    public static String messageId() { return generate("msg"); }
    public static String conversationId() { return generate("conv"); }
    public static String taskId() { return generate("task"); }
    public static String workflowId() { return generate("wf"); }
    public static String eventId() { return generate("evt"); }
    public static String toolCallId() { return generate("tc"); }
    public static String hookId() { return generate("hook"); }
    public static String bufferId() { return generate("buf"); }
}
