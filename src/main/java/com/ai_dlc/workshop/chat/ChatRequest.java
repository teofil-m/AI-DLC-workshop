package com.ai_dlc.workshop.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Incoming RAG chat request payload.
 *
 * @param question the user's question — must not be blank; capped at 2000 chars to
 *                 prevent token-limit errors on the embedding model and LLM context overflow
 */
public record ChatRequest(@NotBlank @Size(max = 2000) String question) {
}
