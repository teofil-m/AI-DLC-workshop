package com.ai_dlc.workshop.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Flux;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * REST controller exposing {@code GET /api/chat/stream} for SSE-based streaming RAG chat.
 *
 * <p>Requires a valid Bearer JWT in the Authorization header. The {@code sub} claim
 * is forwarded to {@link StreamingChatService} to scope the vector store retrieval
 * to the authenticated user's documents.
 *
 * <p>Each LLM token/chunk is emitted as a separate SSE {@code data:} frame. The stream
 * completes when the model finishes generating its response.
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class StreamingChatController {

    private final StreamingChatService streamingChatService;

    /**
     * Streams an LLM-generated answer to the given question using RAG.
     *
     * <p>Spring WebFlux automatically serialises each emitted {@link String} element as an
     * SSE {@code data:} frame when the response content type is {@code text/event-stream}.
     *
     * @param question the user's question (must not be blank)
     * @param jwt      the authenticated JWT principal (must contain a non-blank {@code sub} claim)
     * @return a {@link Flux} of token strings streamed as SSE frames
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<String> stream(
            @RequestParam String question,
            @AuthenticationPrincipal Jwt jwt) {

        if (jwt == null || jwt.getSubject() == null || jwt.getSubject().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "JWT must contain a sub claim");
        }
        if (question == null || question.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "question must not be blank");
        }
        if (question.length() > 2000) {
            throw new ResponseStatusException(BAD_REQUEST, "question must not exceed 2000 characters");
        }

        String userId = jwt.getSubject();
        log.debug("SSE stream request for userId={}", userId);

        return streamingChatService.stream(question, userId);
    }
}
