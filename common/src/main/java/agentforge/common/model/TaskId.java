package agentforge.common.model;

/**
 * Unique identifier for a task within a workflow DAG.
 */
public record TaskId(String value) implements Comparable<TaskId> {

    public static TaskId of(String value) {
        return new TaskId(value);
    }

    @Override
    public int compareTo(TaskId other) {
        return this.value.compareTo(other.value);
    }

    @Override
    public String toString() {
        return value;
    }
}
