package agentforge.orchestrator;

import agentforge.common.model.TaskId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages Human-in-the-Loop (HITL) checkpoint gates.
 *
 * Gates pause workflow execution at designated points and wait for
 * human approval before proceeding. Used for editorial review,
 * compliance sign-off, and quality assurance checkpoints.
 *
 * Each gate has a lifecycle: PENDING → APPROVED or REJECTED.
 * Waiters block on a CompletableFuture until a decision is made.
 */
public final class HITLGateManager {

    private static final Logger log = LoggerFactory.getLogger(HITLGateManager.class);

    public enum GateStatus { PENDING, APPROVED, REJECTED }

    public record GateDecision(boolean approved, String reviewer, String comment) {}

    private record GateEntry(
            String gateId, String workflowId, TaskId taskId,
            String description, GateStatus status,
            CompletableFuture<GateDecision> future) {}

    private final Map<String, GateEntry> gates = new ConcurrentHashMap<>();
    private final AtomicLong idCounter = new AtomicLong(1);

    /**
     * Create a HITL gate that must be approved before the task proceeds.
     *
     * @param workflowId  the workflow this gate belongs to
     * @param taskId      the task that is gated
     * @param description human-readable description of what needs review
     * @return the gate ID
     */
    public String createGate(String workflowId, TaskId taskId, String description) {
        String gateId = "gate-" + idCounter.getAndIncrement();
        var future = new CompletableFuture<GateDecision>();
        gates.put(gateId, new GateEntry(gateId, workflowId, taskId, description,
                GateStatus.PENDING, future));
        log.info("HITL gate created: {} for workflow {} task {} — '{}'",
                gateId, workflowId, taskId, description);
        return gateId;
    }

    /**
     * Wait for a gate decision. Returns a future that completes when
     * the gate is approved or rejected.
     */
    public CompletableFuture<GateDecision> awaitDecision(String gateId) {
        GateEntry entry = gates.get(gateId);
        if (entry == null) {
            return CompletableFuture.failedFuture(
                    new IllegalArgumentException("Gate not found: " + gateId));
        }
        return entry.future();
    }

    /**
     * Approve a gate — unblocks any waiters.
     */
    public void approveGate(String gateId, String reviewer, String comment) {
        GateEntry entry = gates.get(gateId);
        if (entry == null) throw new IllegalArgumentException("Gate not found: " + gateId);

        gates.put(gateId, new GateEntry(entry.gateId(), entry.workflowId(), entry.taskId(),
                entry.description(), GateStatus.APPROVED, entry.future()));
        entry.future().complete(new GateDecision(true, reviewer, comment));
        log.info("HITL gate APPROVED: {} by {} — '{}'", gateId, reviewer, comment);
    }

    /**
     * Reject a gate — unblocks any waiters with rejection.
     */
    public void rejectGate(String gateId, String reviewer, String comment) {
        GateEntry entry = gates.get(gateId);
        if (entry == null) throw new IllegalArgumentException("Gate not found: " + gateId);

        gates.put(gateId, new GateEntry(entry.gateId(), entry.workflowId(), entry.taskId(),
                entry.description(), GateStatus.REJECTED, entry.future()));
        entry.future().complete(new GateDecision(false, reviewer, comment));
        log.info("HITL gate REJECTED: {} by {} — '{}'", gateId, reviewer, comment);
    }

    /**
     * Get the current status of a gate.
     */
    public GateStatus getGateStatus(String gateId) {
        GateEntry entry = gates.get(gateId);
        return entry != null ? entry.status() : null;
    }

    /**
     * List all pending gate IDs for a workflow.
     */
    public List<String> listPendingGates(String workflowId) {
        return gates.values().stream()
                .filter(e -> e.workflowId().equals(workflowId) && e.status() == GateStatus.PENDING)
                .map(GateEntry::gateId)
                .toList();
    }

    /**
     * Total number of gates created.
     */
    public int totalGates() {
        return gates.size();
    }
}
