package agentforge.gateway;

import java.util.List;

/**
 * REST request to create a workflow.
 *
 * @param name       workflow name
 * @param tasks      list of task definitions
 * @param edges      list of edges between tasks
 */
public record WorkflowRequest(
        String name,
        List<TaskRequest> tasks,
        List<EdgeRequest> edges) {

    public record TaskRequest(String id, String agentType) {}
    public record EdgeRequest(String from, String to, String type) {}
}
