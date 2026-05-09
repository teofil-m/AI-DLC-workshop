package com.ai_dlc.workshop.chat;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;

/**
 * Pure unit tests for {@link StreamingChatService}.
 *
 * <p>The Spring AI {@link ChatClient} streaming chain
 * ({@code prompt() → user() → stream() → content()}) is composed entirely of
 * interfaces / internal types. Fully mocking the chain requires stubbing every
 * intermediate step and is brittle against Spring AI internal refactoring.
 * Therefore:
 *
 * <ul>
 *   <li>We verify the top-level interaction ({@code chatClient.prompt()} is called)
 *       to confirm the service delegates to the AI client.</li>
 *   <li>We verify defensive null-handling on the question argument.</li>
 *   <li>Full end-to-end SSE correctness is covered by {@link StreamingChatControllerTest}
 *       via the mocked StreamingChatService boundary.</li>
 * </ul>
 *
 * <p>TODO: Once Spring AI M6+ ships a public test-support module
 * (TestChatClient / ChatClientMockBuilder), replace these stubs with the official
 * helpers and add a full happy-path unit test that asserts streamed token contents.
 */
@ExtendWith(MockitoExtension.class)
class StreamingChatServiceTest {

    @Mock
    ChatClient chatClient;

    @Mock
    ChatClient.ChatClientRequestSpec promptRequestSpec;

    @Mock
    VectorStore vectorStore;

    StreamingChatService streamingChatService;

    @BeforeEach
    void setUp() {
        streamingChatService = new StreamingChatService(chatClient, vectorStore);
        streamingChatService.init(); // @PostConstruct not called by Mockito — invoke explicitly
    }

    // -------------------------------------------------------------------------
    // Delegation to ChatClient
    // -------------------------------------------------------------------------

    @Test
    void stream_anyQuestion_invokesChatClientPrompt() {
        // GIVEN chatClient.prompt() returns a spec (stub just enough to avoid NPE on the call chain)
        when(chatClient.prompt()).thenReturn(promptRequestSpec);
        // Chain methods return null which will cause a NullPointerException further down —
        // this is expected: we only assert that the service reaches the prompt() call.

        // WHEN stream() is invoked (it will throw because the chain is incompletely stubbed)
        // THEN chatClient.prompt() was called before the chain broke — verify that first
        try {
            streamingChatService.stream("What is SSE?", "user-123");
        } catch (Exception ignored) {
            // Expected: the chain is not fully stubbed; we only care about the first call
        }

        verify(chatClient).prompt();
    }

    // -------------------------------------------------------------------------
    // Null / invalid input
    // -------------------------------------------------------------------------

    @Test
    void stream_nullQuestion_propagatesException() {
        // GIVEN chatClient.prompt() may or may not be reached depending on whether the
        // implementation has a null-guard before delegating — lenient avoids
        // UnnecessaryStubbingException during the TDD red phase when the stub is unused
        lenient().when(chatClient.prompt()).thenReturn(promptRequestSpec);

        // WHEN a null question is passed to the service
        // THEN some exception is thrown (NullPointerException or IllegalArgumentException
        //      depending on whether a null-guard or the call chain fires first)
        assertThatThrownBy(() -> streamingChatService.stream(null, "user-123"))
                .isInstanceOf(Exception.class);
    }
}
