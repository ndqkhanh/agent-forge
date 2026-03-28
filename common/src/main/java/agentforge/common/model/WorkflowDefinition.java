package agentforge.common.model;

import java.util.*;

/**
 * Immutable definition of a workflow DAG.
 * Contains task definitions and edges between them.
 */
public record WorkflowDefinition(
        String name,
        List<TaskDefinition> tasks,
        List<TaskEdge> edges) {

    public WorkflowDefinition {
        tasks = List.copyOf(tasks);
        edges = List.copyOf(edges);
    }

    /**
     * Get a task definition by ID.
     */
    public Optional<TaskDefinition> getTask(TaskId id) {
        return tasks.stream().filter(t -> t.id().equals(id)).findFirst();
    }

    /**
     * Get all predecessor task IDs for a given task (considering only UNCONDITIONAL edges).
     */
    public Set<TaskId> predecessors(TaskId taskId) {
        Set<TaskId> result = new HashSet<>();
        for (TaskEdge edge : edges) {
            if (edge.to().equals(taskId) && edge.type() == EdgeType.UNCONDITIONAL) {
                result.add(edge.from());
            }
        }
        return result;
    }

    /**
     * Get all successor task IDs for a given task (considering only UNCONDITIONAL edges).
     */
    public Set<TaskId> successors(TaskId taskId) {
        Set<TaskId> result = new HashSet<>();
        for (TaskEdge edge : edges) {
            if (edge.from().equals(taskId) && edge.type() == EdgeType.UNCONDITIONAL) {
                result.add(edge.to());
            }
        }
        return result;
    }
}
