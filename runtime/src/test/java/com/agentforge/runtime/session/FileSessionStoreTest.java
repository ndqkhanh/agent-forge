package com.agentforge.runtime.session;

import com.agentforge.common.model.ConversationMessage;
import com.agentforge.common.model.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class FileSessionStoreTest {

    @TempDir
    Path tempDir;

    private FileSessionStore store;

    @BeforeEach
    void setUp() {
        store = new FileSessionStore(tempDir);
    }

    @Test
    @DisplayName("save and load round-trips session correctly")
    void saveAndLoad_roundTrip() {
        Session session = Session.empty("session-1")
            .addMessage(ConversationMessage.userText("hello"))
            .addMessage(ConversationMessage.assistantText("world"));

        store.save(session);
        Optional<Session> loaded = store.load("session-1");

        assertThat(loaded).isPresent();
        assertThat(loaded.get().id()).isEqualTo("session-1");
        assertThat(loaded.get().messages()).hasSize(2);
        assertThat(loaded.get().messages().get(0).textContent()).isEqualTo("hello");
        assertThat(loaded.get().messages().get(1).textContent()).isEqualTo("world");
    }

    @Test
    @DisplayName("load nonexistent session returns empty Optional")
    void load_nonexistentSession_returnsEmpty() {
        Optional<Session> result = store.load("does-not-exist");
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("load null sessionId returns empty Optional")
    void load_nullSessionId_returnsEmpty() {
        assertThat(store.load(null)).isEmpty();
    }

    @Test
    @DisplayName("load blank sessionId returns empty Optional")
    void load_blankSessionId_returnsEmpty() {
        assertThat(store.load("   ")).isEmpty();
    }

    @Test
    @DisplayName("listSessionIds returns all saved session IDs")
    void listSessionIds_returnsAllIds() {
        store.save(Session.empty("alpha"));
        store.save(Session.empty("beta"));
        store.save(Session.empty("gamma"));

        List<String> ids = store.listSessionIds();
        assertThat(ids).containsExactlyInAnyOrder("alpha", "beta", "gamma");
    }

    @Test
    @DisplayName("listSessionIds returns empty list when no sessions saved")
    void listSessionIds_noSessions_returnsEmpty() {
        assertThat(store.listSessionIds()).isEmpty();
    }

    @Test
    @DisplayName("delete removes the session")
    void delete_existingSession_removesIt() {
        store.save(Session.empty("to-delete"));
        assertThat(store.load("to-delete")).isPresent();

        store.delete("to-delete");
        assertThat(store.load("to-delete")).isEmpty();
    }

    @Test
    @DisplayName("delete nonexistent session does not throw")
    void delete_nonexistentSession_doesNotThrow() {
        store.delete("nonexistent"); // should not throw
        assertThat(store.listSessionIds()).isEmpty();
    }

    @Test
    @DisplayName("saving updated session overwrites previous version")
    void save_updatedSession_overwrites() {
        Session v1 = Session.empty("my-session")
            .addMessage(ConversationMessage.userText("first"));
        store.save(v1);

        Session v2 = v1.addMessage(ConversationMessage.assistantText("second"));
        store.save(v2);

        Optional<Session> loaded = store.load("my-session");
        assertThat(loaded).isPresent();
        assertThat(loaded.get().messages()).hasSize(2);
    }

    @Test
    @DisplayName("concurrent save and load do not corrupt data")
    void saveAndLoad_concurrent_safeExecution() throws InterruptedException {
        int threadCount = 4;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                Session session = Session.empty("session-" + idx)
                    .addMessage(ConversationMessage.userText("concurrent-" + idx));
                store.save(session);
                store.load("session-" + idx);
            });
        }

        executor.shutdown();
        assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();
        assertThat(store.listSessionIds()).hasSize(threadCount);
    }
}
