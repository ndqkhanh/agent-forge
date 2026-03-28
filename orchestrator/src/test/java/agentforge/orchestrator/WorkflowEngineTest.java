package agentforge.orchestrator;

import agentforge.common.model.*;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD tests for WorkflowEngine — DAG-based workflow execution.
 *
 * Tests cover:
 * - Topological sort of DAG
 * - Sequential execution (A → B → C)
 * - Parallel fan-out (A → [B, C])
 * - Fan-in (join: [B, C] → D)
 * - Diamond pattern (A → [B, C] → D)
 * - Single-task workflow
 * - Task failure propagation
 * - Cycle detection
 * - Input passing between tasks
 */
class WorkflowEngineTest {

    private final List<String> executionOrder = new CopyOnWriteArrayList<>();

    private TaskExecutor fakeExecutor() {
        return (task, inputs) -> CompletableFuture.supplyAsync(() -> {
            executionOrder.add(task.id().value());
            String combinedInputs = inputs.isEmpty() ? "root" :
                    String.join("+", inputs.values());
            return TaskResult.success(task.id(), combinedInputs + "→" + task.id().value(),
                    Duration.ofMillis(10));
        });
    }

    private TaskExecutor delayedExecutor(Duration delay) {
        return (task, inputs) -> CompletableFuture.supplyAsync(() -> {
            try { Thread.sleep(delay.toMillis()); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            executionOrder.add(task.id().value());
            return TaskResult.success(task.id(), task.id().value(), delay);
        });
    }

    // ========== Topological Sort ==========

    @Test
    @DisplayName("topologicalSort returns correct order for linear DAG")
    void topologicalSortLinear() {
        var a = new TaskDefinition(TaskId.of("A"), "agent");
        var b = new TaskDefinition(TaskId.of("B"), "agent");
        var c = new TaskDefinition(TaskId.of("C"), "agent");
        var def = new WorkflowDefinition("test", List.of(a, b, c), List.of(
                TaskEdge.unconditional(TaskId.of("A"), TaskId.of("B")),
                TaskEdge.unconditional(TaskId.of("B"), TaskId.of("C"))
        ));

        List<TaskId> sorted = WorkflowEngine.topologicalSort(def);

        assertThat(sorted).containsExactly(TaskId.of("A"), TaskId.of("B"), TaskId.of("C"));
    }

    @Test
    @DisplayName("topologicalSort handles fan-out correctly")
    void topologicalSortFanOut() {
        var a = new TaskDefinition(TaskId.of("A"), "agent");
        var b = new TaskDefinition(TaskId.of("B"), "agent");
        var c = new TaskDefinition(TaskId.of("C"), "agent");
        var def = new WorkflowDefinition("test", List.of(a, b, c), List.of(
                TaskEdge.unconditional(TaskId.of("A"), TaskId.of("B")),
                TaskEdge.unconditional(TaskId.of("A"), TaskId.of("C"))
        ));

        List<TaskId> sorted = WorkflowEngine.topologicalSort(def);

        // A must come first; B and C can be in any order
        assertThat(sorted.getFirst()).isEqualTo(TaskId.of("A"));
        assertThat(sorted).containsExactlyInAnyOrder(TaskId.of("A"), TaskId.of("B"), TaskId.of("C"));
    }

    @Test
    @DisplayName("topologicalSort detects cycle")
    void topologicalSortDetectsCycle() {
        var a = new TaskDefinition(TaskId.of("A"), "agent");
        var b = new TaskDefinition(TaskId.of("B"), "agent");
        var def = new WorkflowDefinition("test", List.of(a, b), List.of(
                TaskEdge.unconditional(TaskId.of("A"), TaskId.of("B")),
                TaskEdge.unconditional(TaskId.of("B"), TaskId.of("A"))
        ));

        assertThatThrownBy(() -> WorkflowEngine.topologicalSort(def))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cycle");
    }

    // ========== Sequential Execution ==========

    @Test
    @DisplayName("execute runs tasks in sequence for linear DAG: A → B → C")
    void executeLinearDAG() throws Exception {
        var a = new TaskDefinition(TaskId.of("A"), "agent");
        var b = new TaskDefinition(TaskId.of("B"), "agent");
        var c = new TaskDefinition(TaskId.of("C"), "agent");
        var def = new WorkflowDefinition("linear", List.of(a, b, c), List.of(
                TaskEdge.unconditional(TaskId.of("A"), TaskId.of("B")),
                TaskEdge.unconditional(TaskId.of("B"), TaskId.of("C"))
        ));

        var engine = new WorkflowEngine(fakeExecutor());
        Map<TaskId, TaskResult> results = engine.execute(def).get(5, TimeUnit.SECONDS);

        assertThat(results).hasSize(3);
        assertThat(results.values()).allSatisfy(r -> assertThat(r.isSuccess()).isTrue());
        // A ran before B, B before C
        assertThat(executionOrder).containsExactly("A", "B", "C");
    }

    // ========== Parallel Fan-Out ==========

    @Test
    @DisplayName("execute runs independent tasks in parallel: A → [B, C]")
    void executeFanOut() throws Exception {
        var a = new TaskDefinition(TaskId.of("A"), "agent");
        var b = new TaskDefinition(TaskId.of("B"), "agent");
        var c = new TaskDefinition(TaskId.of("C"), "agent");
        var def = new WorkflowDefinition("fanout", List.of(a, b, c), List.of(
                TaskEdge.unconditional(TaskId.of("A"), TaskId.of("B")),
                TaskEdge.unconditional(TaskId.of("A"), TaskId.of("C"))
        ));

        var engine = new WorkflowEngine(fakeExecutor());
        Map<TaskId, TaskResult> results = engine.execute(def).get(5, TimeUnit.SECONDS);

        assertThat(results).hasSize(3);
        // A was first
        assertThat(executionOrder.getFirst()).isEqualTo("A");
        // B and C ran after A (order between them is non-deterministic)
        assertThat(executionOrder).containsAll(List.of("B", "C"));
    }

    // ========== Fan-In (Join) ==========

    @Test
    @DisplayName("execute waits for all predecessors before fan-in: [A, B] → C")
    void executeFanIn() throws Exception {
        var a = new TaskDefinition(TaskId.of("A"), "agent");
        var b = new TaskDefinition(TaskId.of("B"), "agent");
        var c = new TaskDefinition(TaskId.of("C"), "agent");
        var def = new WorkflowDefinition("fanin", List.of(a, b, c), List.of(
                TaskEdge.unconditional(TaskId.of("A"), TaskId.of("C")),
                TaskEdge.unconditional(TaskId.of("B"), TaskId.of("C"))
        ));

        var engine = new WorkflowEngine(fakeExecutor());
        Map<TaskId, TaskResult> results = engine.execute(def).get(5, TimeUnit.SECONDS);

        assertThat(results).hasSize(3);
        // C must be last
        assertThat(executionOrder.getLast()).isEqualTo("C");
    }

    // ========== Diamond Pattern ==========

    @Test
    @DisplayName("execute handles diamond: A → [B, C] → D")
    void executeDiamond() throws Exception {
        var a = new TaskDefinition(TaskId.of("A"), "agent");
        var b = new TaskDefinition(TaskId.of("B"), "agent");
        var c = new TaskDefinition(TaskId.of("C"), "agent");
        var d = new TaskDefinition(TaskId.of("D"), "agent");
        var def = new WorkflowDefinition("diamond", List.of(a, b, c, d), List.of(
                TaskEdge.unconditional(TaskId.of("A"), TaskId.of("B")),
                TaskEdge.unconditional(TaskId.of("A"), TaskId.of("C")),
                TaskEdge.unconditional(TaskId.of("B"), TaskId.of("D")),
                TaskEdge.unconditional(TaskId.of("C"), TaskId.of("D"))
        ));

        var engine = new WorkflowEngine(fakeExecutor());
        Map<TaskId, TaskResult> results = engine.execute(def).get(5, TimeUnit.SECONDS);

        assertThat(results).hasSize(4);
        assertThat(executionOrder.getFirst()).isEqualTo("A");
        assertThat(executionOrder.getLast()).isEqualTo("D");
    }

    // ========== Single Task ==========

    @Test
    @DisplayName("execute handles single-task workflow")
    void executeSingleTask() throws Exception {
        var a = new TaskDefinition(TaskId.of("A"), "agent");
        var def = new WorkflowDefinition("single", List.of(a), List.of());

        var engine = new WorkflowEngine(fakeExecutor());
        Map<TaskId, TaskResult> results = engine.execute(def).get(5, TimeUnit.SECONDS);

        assertThat(results).hasSize(1);
        assertThat(results.get(TaskId.of("A")).isSuccess()).isTrue();
    }

    // ========== Input Passing ==========

    @Test
    @DisplayName("task receives predecessor outputs as inputs")
    void taskReceivesPredecessorOutputs() throws Exception {
        var a = new TaskDefinition(TaskId.of("A"), "agent");
        var b = new TaskDefinition(TaskId.of("B"), "agent");
        var def = new WorkflowDefinition("chain", List.of(a, b), List.of(
                TaskEdge.unconditional(TaskId.of("A"), TaskId.of("B"))
        ));

        var engine = new WorkflowEngine(fakeExecutor());
        Map<TaskId, TaskResult> results = engine.execute(def).get(5, TimeUnit.SECONDS);

        // B's output should contain A's output as input
        String bOutput = results.get(TaskId.of("B")).output();
        assertThat(bOutput).contains("A");
    }

    // ========== Failure Propagation ==========

    @Test
    @DisplayName("task failure prevents dependent tasks from executing")
    void failurePreventsDownstream() throws Exception {
        TaskExecutor failOnB = (task, inputs) -> CompletableFuture.supplyAsync(() -> {
            executionOrder.add(task.id().value());
            if (task.id().value().equals("B")) {
                return TaskResult.failed(task.id(), "B failed", Duration.ofMillis(5));
            }
            return TaskResult.success(task.id(), "ok", Duration.ofMillis(5));
        });

        var a = new TaskDefinition(TaskId.of("A"), "agent");
        var b = new TaskDefinition(TaskId.of("B"), "agent");
        var c = new TaskDefinition(TaskId.of("C"), "agent");
        var def = new WorkflowDefinition("fail", List.of(a, b, c), List.of(
                TaskEdge.unconditional(TaskId.of("A"), TaskId.of("B")),
                TaskEdge.unconditional(TaskId.of("B"), TaskId.of("C"))
        ));

        var engine = new WorkflowEngine(failOnB);
        Map<TaskId, TaskResult> results = engine.execute(def).get(5, TimeUnit.SECONDS);

        assertThat(results.get(TaskId.of("A")).isSuccess()).isTrue();
        assertThat(results.get(TaskId.of("B")).isSuccess()).isFalse();
        // C should not have executed
        assertThat(executionOrder).doesNotContain("C");
    }

    // ========== Parallel Speedup ==========

    @Test
    @DisplayName("parallel tasks execute concurrently, not sequentially")
    void parallelTasksExecuteConcurrently() throws Exception {
        var a = new TaskDefinition(TaskId.of("A"), "agent");
        var b = new TaskDefinition(TaskId.of("B"), "agent");
        var c = new TaskDefinition(TaskId.of("C"), "agent");
        var def = new WorkflowDefinition("parallel", List.of(a, b, c), List.of(
                TaskEdge.unconditional(TaskId.of("A"), TaskId.of("B")),
                TaskEdge.unconditional(TaskId.of("A"), TaskId.of("C"))
        ));

        // Each task takes 200ms. If parallel: ~400ms total. If sequential: ~600ms.
        var engine = new WorkflowEngine(delayedExecutor(Duration.ofMillis(200)));
        long start = System.currentTimeMillis();
        engine.execute(def).get(5, TimeUnit.SECONDS);
        long elapsed = System.currentTimeMillis() - start;

        // Should be well under 600ms (sequential would be 600ms)
        assertThat(elapsed).isLessThan(550);
    }
}
