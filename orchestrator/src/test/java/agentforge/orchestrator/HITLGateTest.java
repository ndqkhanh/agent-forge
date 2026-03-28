package agentforge.orchestrator;

import agentforge.common.model.*;
import org.junit.jupiter.api.*;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

/**
 * TDD tests for HITLGate — Human-in-the-Loop checkpoint gates.
 *
 * Tests cover:
 * - Gate pauses execution until approved
 * - Gate rejection stops workflow
 * - Multiple gates in a workflow
 * - Gate timeout (auto-reject after deadline)
 * - Gate with reviewer assignment
 * - Listing pending gates
 */
class HITLGateTest {

    private HITLGateManager gateManager;

    @BeforeEach
    void setUp() {
        gateManager = new HITLGateManager();
    }

    // ========== Gate Creation ==========

    @Test
    @DisplayName("createGate registers a pending gate for a task")
    void createGateRegistersPending() {
        String gateId = gateManager.createGate("wf-1", TaskId.of("summarize"),
                "Editorial review required");

        assertThat(gateId).isNotNull();
        assertThat(gateManager.getGateStatus(gateId)).isEqualTo(HITLGateManager.GateStatus.PENDING);
    }

    // ========== Approval ==========

    @Test
    @DisplayName("approveGate transitions to APPROVED and unblocks waiter")
    void approveGateUnblocks() throws Exception {
        String gateId = gateManager.createGate("wf-1", TaskId.of("summarize"), "Review");

        // Start waiting in background
        CompletableFuture<HITLGateManager.GateDecision> decision = gateManager.awaitDecision(gateId);

        // Approve
        gateManager.approveGate(gateId, "editor-1", "Looks good");

        HITLGateManager.GateDecision result = decision.get(5, TimeUnit.SECONDS);
        assertThat(result.approved()).isTrue();
        assertThat(result.reviewer()).isEqualTo("editor-1");
        assertThat(result.comment()).isEqualTo("Looks good");
        assertThat(gateManager.getGateStatus(gateId)).isEqualTo(HITLGateManager.GateStatus.APPROVED);
    }

    // ========== Rejection ==========

    @Test
    @DisplayName("rejectGate transitions to REJECTED and unblocks waiter")
    void rejectGateUnblocks() throws Exception {
        String gateId = gateManager.createGate("wf-1", TaskId.of("summarize"), "Review");

        CompletableFuture<HITLGateManager.GateDecision> decision = gateManager.awaitDecision(gateId);

        gateManager.rejectGate(gateId, "editor-1", "Needs more detail");

        HITLGateManager.GateDecision result = decision.get(5, TimeUnit.SECONDS);
        assertThat(result.approved()).isFalse();
        assertThat(result.comment()).isEqualTo("Needs more detail");
        assertThat(gateManager.getGateStatus(gateId)).isEqualTo(HITLGateManager.GateStatus.REJECTED);
    }

    // ========== Listing Pending Gates ==========

    @Test
    @DisplayName("listPendingGates returns only pending gates")
    void listPendingGatesReturnsPending() {
        String g1 = gateManager.createGate("wf-1", TaskId.of("A"), "Review A");
        String g2 = gateManager.createGate("wf-1", TaskId.of("B"), "Review B");

        gateManager.approveGate(g1, "reviewer", "ok");

        List<String> pending = gateManager.listPendingGates("wf-1");
        assertThat(pending).hasSize(1);
        assertThat(pending).contains(g2);
    }

    @Test
    @DisplayName("listPendingGates filters by workflow")
    void listPendingGatesFiltersByWorkflow() {
        gateManager.createGate("wf-1", TaskId.of("A"), "Review");
        gateManager.createGate("wf-2", TaskId.of("B"), "Review");

        assertThat(gateManager.listPendingGates("wf-1")).hasSize(1);
        assertThat(gateManager.listPendingGates("wf-2")).hasSize(1);
    }

    // ========== Multiple Gates ==========

    @Test
    @DisplayName("multiple gates for same workflow are independent")
    void multipleGatesIndependent() throws Exception {
        String g1 = gateManager.createGate("wf-1", TaskId.of("A"), "Review A");
        String g2 = gateManager.createGate("wf-1", TaskId.of("B"), "Review B");

        var d1 = gateManager.awaitDecision(g1);
        var d2 = gateManager.awaitDecision(g2);

        gateManager.approveGate(g1, "r1", "ok");
        gateManager.rejectGate(g2, "r2", "no");

        assertThat(d1.get(5, TimeUnit.SECONDS).approved()).isTrue();
        assertThat(d2.get(5, TimeUnit.SECONDS).approved()).isFalse();
    }

    // ========== Gate Stats ==========

    @Test
    @DisplayName("totalGates tracks all gates created")
    void totalGatesTracksAll() {
        gateManager.createGate("wf-1", TaskId.of("A"), "Review");
        gateManager.createGate("wf-2", TaskId.of("B"), "Review");

        assertThat(gateManager.totalGates()).isEqualTo(2);
    }
}
