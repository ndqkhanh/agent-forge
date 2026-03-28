package agentforge.common.model;

import java.util.UUID;

/**
 * Unique identifier for a workflow instance.
 */
public record WorkflowId(String value) {

    public static WorkflowId generate() {
        return new WorkflowId(UUID.randomUUID().toString());
    }

    public static WorkflowId of(String value) {
        return new WorkflowId(value);
    }

    @Override
    public String toString() {
        return value;
    }
}
