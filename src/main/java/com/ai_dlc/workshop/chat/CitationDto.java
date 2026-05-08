package com.ai_dlc.workshop.chat;

/**
 * DTO representing a single source document that contributed to a RAG answer.
 *
 * @param source     human-readable source identifier (e.g. filename or URL)
 * @param docId      the document ID in the repository
 * @param chunkIndex the chunk index within the source document
 */
public record CitationDto(String source, String docId, String chunkIndex) {
}
