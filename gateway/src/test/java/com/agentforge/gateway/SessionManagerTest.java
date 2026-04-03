package com.agentforge.gateway;

import com.agentforge.api.provider.ApiClient;
import com.agentforge.api.provider.ApiRequest;
import com.agentforge.api.stream.AssistantEvent;
import com.agentforge.common.model.ModelInfo;
import com.agentforge.common.model.Session;
import com.agentforge.common.model.TokenUsage;
import com.agentforge.runtime.ConversationRuntime;
import com.agentforge.runtime.HookPipeline;
import com.agentforge.runtime.PermissionChecker;
import com.agentforge.runtime.ToolExecutor;
import com.agentforge.runtime.prompt.SystemPromptBuilder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SessionManagerTest {

    private SessionManager manager;
    private ConversationRuntime runtime;

    @BeforeEach
    void setUp() {
        manager = new SessionManager();
        runtime = buildRuntime(UUID.randomUUID().toString());
    }

    @Test
    void create_addsSession_canBeRetrieved() {
        String id = "session-1";
        SessionManager.SessionEntry entry = manager.create(id, runtime, "claude-sonnet-4-6");

        assertThat(entry.id()).isEqualTo(id);
        assertThat(entry.model()).isEqualTo("claude-sonnet-4-6");
        assertThat(entry.runtime()).isSameAs(runtime);
        assertThat(entry.createdAt()).isNotNull();
    }

    @Test
    void get_existingSession_returnsEntry() {
        String id = "session-get";
        manager.create(id, runtime, "claude-sonnet-4-6");

        Optional<SessionManager.SessionEntry> result = manager.get(id);
        assertThat(result).isPresent();
        assertThat(result.get().id()).isEqualTo(id);
    }

    @Test
    void get_missingSession_returnsEmpty() {
        Optional<SessionManager.SessionEntry> result = manager.get("nonexistent");
        assertThat(result).isEmpty();
    }

    @Test
    void get_nullId_returnsEmpty() {
        assertThat(manager.get(null)).isEmpty();
    }

    @Test
    void remove_existingSession_returnsTrue() {
        String id = "to-remove";
        manager.create(id, runtime, "model");

        boolean removed = manager.remove(id);
        assertThat(removed).isTrue();
        assertThat(manager.get(id)).isEmpty();
    }

    @Test
    void remove_missingSession_returnsFalse() {
        assertThat(manager.remove("ghost")).isFalse();
    }

    @Test
    void remove_nullId_returnsFalse() {
        assertThat(manager.remove(null)).isFalse();
    }

    @Test
    void size_emptyManager_returnsZero() {
        assertThat(manager.size()).isZero();
    }

    @Test
    void size_afterCreatingTwoSessions_returnsTwo() {
        manager.create("s1", buildRuntime("s1"), "model");
        manager.create("s2", buildRuntime("s2"), "model");

        assertThat(manager.size()).isEqualTo(2);
    }

    @Test
    void size_afterRemoval_decrements() {
        manager.create("s1", buildRuntime("s1"), "model");
        manager.create("s2", buildRuntime("s2"), "model");
        manager.remove("s1");

        assertThat(manager.size()).isEqualTo(1);
    }

    @Test
    void sessionIds_returnsAllIds() {
        manager.create("a", buildRuntime("a"), "m");
        manager.create("b", buildRuntime("b"), "m");

        assertThat(manager.sessionIds()).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void contains_existingSession_returnsTrue() {
        manager.create("x", runtime, "m");
        assertThat(manager.contains("x")).isTrue();
    }

    @Test
    void contains_missingSession_returnsFalse() {
        assertThat(manager.contains("nope")).isFalse();
    }

    @Test
    void contains_nullId_returnsFalse() {
        assertThat(manager.contains(null)).isFalse();
    }

    @Test
    void create_nullId_throwsIllegalArgument() {
        assertThatThrownBy(() -> manager.create(null, runtime, "model"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_blankId_throwsIllegalArgument() {
        assertThatThrownBy(() -> manager.create("  ", runtime, "model"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_nullRuntime_throwsIllegalArgument() {
        assertThatThrownBy(() -> manager.create("id", null, "model"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void create_nullModel_throwsIllegalArgument() {
        assertThatThrownBy(() -> manager.create("id", runtime, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void sessionEntry_session_delegatesToRuntime() {
        String id = "delegate-test";
        SessionManager.SessionEntry entry = manager.create(id, runtime, "model");

        Session session = entry.session();
        assertThat(session).isNotNull();
        assertThat(session.id()).isEqualTo(runtime.getSession().id());
    }

    // -------------------------------------------------------------------------
    // Helper: build a minimal ConversationRuntime for tests
    // -------------------------------------------------------------------------

    private static ConversationRuntime buildRuntime(String sessionId) {
        ApiClient noopClient = new ApiClient() {
            @Override
            public Stream<AssistantEvent> streamMessage(ApiRequest request) {
                return Stream.of(
                    new AssistantEvent.TextDelta("hello"),
                    new AssistantEvent.MessageStop("end_turn")
                );
            }
            @Override public String providerName() { return "noop"; }
            @Override public List<ModelInfo> availableModels() { return List.of(); }
        };

        ToolExecutor noopTools = new ToolExecutor() {
            @Override public String execute(String n, String i) { return "{}"; }
            @Override public boolean supports(String n) { return false; }
        };

        HookPipeline noopHooks = new HookPipeline() {
            @Override public HookResult preToolUse(String n, String i) {
                return HookResult.allow(i);
            }
            @Override public String postToolUse(String n, String r) { return r; }
        };

        PermissionChecker allAllow = (n, l) -> true;

        SystemPromptBuilder promptBuilder = SystemPromptBuilder.builder()
            .basePrompt("test prompt")
            .build();

        return ConversationRuntime.builder()
            .session(Session.empty(sessionId))
            .apiClient(noopClient)
            .toolExecutor(noopTools)
            .hookPipeline(noopHooks)
            .permissionChecker(allAllow)
            .promptBuilder(promptBuilder)
            .build();
    }
}
