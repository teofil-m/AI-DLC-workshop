package com.ai_dlc.workshop.chat;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import jakarta.validation.Valid;

import static org.springframework.http.HttpStatus.BAD_REQUEST;

/**
 * REST controller exposing {@code POST /api/chat} for RAG-backed question answering.
 *
 * <p>Requires a valid Bearer JWT in the Authorization header. The {@code sub} claim
 * is forwarded to {@link ChatService} for future per-user context filtering.
 */
@Slf4j
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    /**
     * Accepts a user question and returns an LLM-generated answer with citations.
     *
     * @param request validated chat request containing the user question
     * @param jwt     the authenticated JWT principal (must contain {@code sub} claim)
     * @return 200 OK with {@link ChatResponse}; 400 if question is blank or sub is missing
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(
            @Valid @RequestBody ChatRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        if (jwt == null) {
            throw new ResponseStatusException(BAD_REQUEST, "JWT must contain a sub claim");
        }
        String userId = jwt.getSubject();
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "JWT must contain a sub claim");
        }

        ChatResponse response = chatService.chat(request.question(), userId);
        return ResponseEntity.ok(response);
    }
}
