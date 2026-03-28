package agentforge.events;

/**
 * Topic definitions for the AgentForge event bus.
 */
public enum Topics {
    WORKFLOW_SUBMITTED("agentforge.workflow.submitted"),
    WORKFLOW_COMPLETED("agentforge.workflow.completed"),
    TASK_DISPATCHED("agentforge.task.dispatched"),
    TASK_COMPLETED("agentforge.task.completed"),
    SPECULATION_STARTED("agentforge.speculation.started"),
    SPECULATION_RESOLVED("agentforge.speculation.resolved");

    private final String topicName;

    Topics(String topicName) {
        this.topicName = topicName;
    }

    public String topicName() {
        return topicName;
    }
}
