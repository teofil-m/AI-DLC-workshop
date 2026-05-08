package com.ai_dlc.workshop.chat;

import jakarta.validation.constraints.NotBlank;

/**
 * Incoming RAG chat request payload.
 *
 * @param question the user's question — must not be blank
 */
public record ChatRequest(@NotBlank String question) {
}
