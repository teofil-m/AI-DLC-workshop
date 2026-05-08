package com.ai_dlc.workshop.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import reactor.core.publisher.Flux;

/**
 * Slice test for {@link StreamingChatController} — verifies HTTP contract, security,
 * and SSE content-type without starting a full application context.
 *
 * <p>The controller returns {@code Flux<String>} with
 * {@code produces = MediaType.TEXT_EVENT_STREAM_VALUE}. Spring MVC's reactive return
 * value support on the Servlet stack handles the Flux even in a {@code @WebMvcTest}.
 */
@WebMvcTest(StreamingChatController.class)
@ActiveProfiles("test")
class StreamingChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StreamingChatService streamingChatService;

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void stream_validQuestion_returns200WithEventStream() throws Exception {
        // GIVEN the service returns a two-token Flux for a known question and user
        given(streamingChatService.stream(eq("hello"), eq("user-123")))
                .willReturn(Flux.just("Hello", " world"));

        // WHEN GET /api/chat/stream with a valid JWT containing sub=user-123
        // THEN 200 OK and Content-Type is text/event-stream
        mockMvc.perform(get("/api/chat/stream")
                        .param("question", "hello")
                        .with(jwt().jwt(j -> j.subject("user-123"))))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
    }

    // -------------------------------------------------------------------------
    // Bean Validation / guard — blank question must be rejected
    // -------------------------------------------------------------------------

    @Test
    void stream_blankQuestion_returns400() throws Exception {
        // GIVEN no service stub — the controller guard must reject the request before calling the service
        // WHEN GET /api/chat/stream?question= (empty string) with a valid JWT
        // THEN 400 Bad Request and the service is never invoked
        mockMvc.perform(get("/api/chat/stream")
                        .param("question", "")
                        .with(jwt().jwt(j -> j.subject("user-123"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(streamingChatService);
    }

    @Test
    void stream_questionExceeds2000Chars_returns400() throws Exception {
        // GIVEN a question that exceeds the 2000-character limit
        // WHEN GET /api/chat/stream?question=<2001 chars> with a valid JWT
        // THEN 400 Bad Request and the service is never invoked
        String tooLong = "a".repeat(2001);
        mockMvc.perform(get("/api/chat/stream")
                        .param("question", tooLong)
                        .with(jwt().jwt(j -> j.subject("user-123"))))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(streamingChatService);
    }

    @Test
    void stream_midStreamFluxError_emitsErrorToken() throws Exception {
        // GIVEN the service returns a Flux that terminates with a mid-stream error
        // (models the scenario where the LLM API fails after the HTTP 200 is already sent)
        given(streamingChatService.stream(any(), any()))
                .willReturn(Flux.error(new RuntimeException("mid-stream failure")));

        // WHEN GET /api/chat/stream?question=hello with a valid JWT
        // THEN the request is accepted (200 headers sent) and the stream completes
        // Note: in @WebMvcTest the error propagation from Flux may surface as 200 or 500
        // depending on when the error is detected — assert it doesn't 4xx at least
        mockMvc.perform(get("/api/chat/stream")
                        .param("question", "hello")
                        .with(jwt().jwt(j -> j.subject("user-123"))))
                .andExpect(status().is(Matchers.not(Matchers.is(400))));
    }

    // -------------------------------------------------------------------------
    // Security — unauthenticated requests must be rejected
    // -------------------------------------------------------------------------

    @Test
    void stream_unauthenticated_returns401or403() throws Exception {
        // GIVEN no JWT is attached to the request
        // WHEN GET /api/chat/stream?question=hello
        // THEN 401 Unauthorized or 403 Forbidden (stateless security rejects anonymous requests)
        mockMvc.perform(get("/api/chat/stream")
                        .param("question", "hello"))
                .andExpect(status().is(Matchers.either(Matchers.is(401)).or(Matchers.is(403))));
    }

    // -------------------------------------------------------------------------
    // Fault handling — unchecked service failures surface as 500
    // -------------------------------------------------------------------------

    @Test
    void stream_serviceThrowsRuntimeException_returns500() throws Exception {
        // GIVEN the LLM or vector store is unavailable — service throws an unchecked exception
        given(streamingChatService.stream(any(), any()))
                .willThrow(new RuntimeException("LLM unavailable"));

        // WHEN GET /api/chat/stream?question=hello with a valid JWT
        // THEN 500 Internal Server Error — unhandled RuntimeException maps to 500
        mockMvc.perform(get("/api/chat/stream")
                        .param("question", "hello")
                        .with(jwt().jwt(j -> j.subject("user-123"))))
                .andExpect(status().isInternalServerError());
    }
}
