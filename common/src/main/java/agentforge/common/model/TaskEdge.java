package agentforge.common.model;

/**
 * A directed edge in the workflow DAG from one task to another.
 *
 * @param from source task
 * @param to   target task
 * @param type edge semantics
 */
public record TaskEdge(TaskId from, TaskId to, EdgeType type) {

    public static TaskEdge unconditional(TaskId from, TaskId to) {
        return new TaskEdge(from, to, EdgeType.UNCONDITIONAL);
    }

    public static TaskEdge speculative(TaskId from, TaskId to) {
        return new TaskEdge(from, to, EdgeType.SPECULATIVE);
    }
}
