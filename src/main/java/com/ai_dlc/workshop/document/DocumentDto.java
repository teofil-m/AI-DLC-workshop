package com.ai_dlc.workshop.document;

import java.time.Instant;
import java.util.UUID;

/**
 * API contract record for document responses. Never exposes the JPA entity directly.
 */
public record DocumentDto(
        UUID id,
        String filename,
        String contentType,
        long sizeBytes,
        String status,
        Instant createdAt) {

    /** Maps from a Document entity to this DTO. */
    public static DocumentDto from(Document document) {
        return new DocumentDto(
                document.getId(),
                document.getFilename(),
                document.getContentType(),
                document.getSizeBytes(),
                document.getStatus().name(),
                document.getCreatedAt());
    }
}
