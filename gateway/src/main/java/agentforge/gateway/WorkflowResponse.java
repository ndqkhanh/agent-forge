package agentforge.gateway;

import agentforge.common.model.WorkflowStatus;

import java.util.Map;

/**
 * REST response for workflow operations.
 *
 * @param workflowId  the workflow instance ID
 * @param name        workflow name
 * @param status      current status
 * @param results     task results (only populated when complete)
 * @param error       error message if failed
 */
public record WorkflowResponse(
        String workflowId,
        String name,
        WorkflowStatus status,
        Map<String, String> results,
        String error) {

    public static WorkflowResponse accepted(String workflowId, String name) {
        return new WorkflowResponse(workflowId, name, WorkflowStatus.PENDING, Map.of(), null);
    }

    public static WorkflowResponse running(String workflowId, String name) {
        return new WorkflowResponse(workflowId, name, WorkflowStatus.RUNNING, Map.of(), null);
    }

    public static WorkflowResponse completed(String workflowId, String name, Map<String, String> results) {
        return new WorkflowResponse(workflowId, name, WorkflowStatus.COMPLETED, results, null);
    }

    public static WorkflowResponse failed(String workflowId, String name, String error) {
        return new WorkflowResponse(workflowId, name, WorkflowStatus.FAILED, Map.of(), error);
    }
}
