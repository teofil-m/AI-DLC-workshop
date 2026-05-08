package com.ai_dlc.workshop.chat;

import java.util.List;

/**
 * RAG chat response returned from {@code POST /api/chat}.
 *
 * @param answer    the model-generated answer
 * @param citations source documents used to construct the answer
 */
public record ChatResponse(String answer, List<CitationDto> citations) {
}
