package agentforge.agent;

/**
 * Result of an A2A delegated task.
 *
 * @param taskId    the delegated task ID
 * @param agentId   the agent that handled it
 * @param status    final status
 * @param output    result output (on success)
 * @param error     error message (on failure)
 */
public record A2ATaskResult(
        String taskId,
        String agentId,
        A2ATaskStatus status,
        String output,
        String error) {

    public static A2ATaskResult success(String taskId, String agentId, String output) {
        return new A2ATaskResult(taskId, agentId, A2ATaskStatus.COMPLETED, output, null);
    }

    public static A2ATaskResult failure(String taskId, String agentId, String error) {
        return new A2ATaskResult(taskId, agentId, A2ATaskStatus.FAILED, null, error);
    }

    public boolean isSuccess() {
        return status == A2ATaskStatus.COMPLETED;
    }
}
