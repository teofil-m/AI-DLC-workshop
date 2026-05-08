package com.ai_dlc.workshop.chat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Slice test for {@link ChatController} — verifies HTTP contract, security, and
 * Bean Validation without starting a full application context.
 */
@WebMvcTest(ChatController.class)
@ActiveProfiles("test")
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ChatService chatService;

    // -------------------------------------------------------------------------
    // Happy path
    // -------------------------------------------------------------------------

    @Test
    void chat_validQuestion_returns200WithAnswer() throws Exception {
        // GIVEN a mocked service that returns a valid RAG response for a known question and user
        ChatResponse serviceResponse = new ChatResponse("RAG is...", List.of());
        given(chatService.chat(eq("What is RAG?"), eq("user-123")))
                .willReturn(serviceResponse);

        // WHEN POST /api/chat with a valid JWT and a well-formed question
        // THEN 200 OK, answer field matches, citations is an array
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is RAG?\"}")
                        .with(jwt().jwt(j -> j.subject("user-123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("RAG is..."))
                .andExpect(jsonPath("$.citations").isArray());
    }

    // -------------------------------------------------------------------------
    // Bean Validation — invalid request payloads
    // -------------------------------------------------------------------------

    @Test
    void chat_blankQuestion_returns400() throws Exception {
        // GIVEN a blank question value (fails @NotBlank before the service is called)
        // WHEN POST /api/chat with a valid JWT
        // THEN 400 Bad Request — Bean Validation rejects it without invoking ChatService
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"\"}")
                        .with(jwt().jwt(j -> j.subject("user-123"))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chat_missingQuestion_returns400() throws Exception {
        // GIVEN a request body that omits the required `question` field entirely
        // WHEN POST /api/chat with a valid JWT
        // THEN 400 Bad Request — Bean Validation rejects the missing field
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}")
                        .with(jwt().jwt(j -> j.subject("user-123"))))
                .andExpect(status().isBadRequest());
    }

    // -------------------------------------------------------------------------
    // Security — unauthenticated requests must be rejected
    // -------------------------------------------------------------------------

    @Test
    void chat_unauthenticated_returns401or403() throws Exception {
        // GIVEN no JWT is attached to the request
        // WHEN POST /api/chat
        // THEN 401 Unauthorized or 403 Forbidden (stateless security rejects anonymous)
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"hello\"}"))
                .andExpect(status().is(Matchers.either(Matchers.is(401)).or(Matchers.is(403))));
    }

    // -------------------------------------------------------------------------
    // Fault handling — service failures surface as 500
    // -------------------------------------------------------------------------

    @Test
    void chat_serviceThrowsRuntimeException_returns500() throws Exception {
        // GIVEN the LLM or vector store is unavailable and ChatService throws an unchecked exception
        given(chatService.chat(any(), any()))
                .willThrow(new RuntimeException("LLM unavailable"));

        // WHEN POST /api/chat with a valid JWT and a well-formed question
        // THEN ProblemDetailControllerAdvice converts the exception to 500
        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"What is RAG?\"}")
                        .with(jwt().jwt(j -> j.subject("user-123"))))
                .andExpect(status().isInternalServerError());
    }
}
