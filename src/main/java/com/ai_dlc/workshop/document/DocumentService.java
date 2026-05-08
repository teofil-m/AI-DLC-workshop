package com.ai_dlc.workshop.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service encapsulating all business logic for document upload and persistence.
 * No embedding, vector store, or AI operations happen here (deferred to later slices).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private static final long MAX_SIZE_BYTES = 2L * 1024 * 1024; // 2 MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("text/plain", "text/markdown");

    private final DocumentRepository documentRepository;

    /**
     * Validates and persists an uploaded file.
     *
     * <p>Validation rules:
     * <ul>
     *   <li>File must not be empty.</li>
     *   <li>Content-type must be {@code text/plain} or {@code text/markdown}.</li>
     *   <li>File size must not exceed 2 MB.</li>
     * </ul>
     *
     * @param file the multipart file received from the HTTP request
     * @return a {@link DocumentDto} representing the persisted document
     * @throws ResponseStatusException if validation fails
     */
    @Transactional
    public DocumentDto upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "File must not be empty");
        }

        String contentType = normalizeContentType(file.getContentType());
        if (!ALLOWED_CONTENT_TYPES.contains(contentType)) {
            log.warn("Rejected upload with unsupported content-type: {}", contentType);
            throw new ResponseStatusException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Only text/plain and text/markdown are accepted");
        }

        if (file.getSize() > MAX_SIZE_BYTES) {
            log.warn("Rejected upload of {} bytes (limit {} bytes)", file.getSize(), MAX_SIZE_BYTES);
            throw new ResponseStatusException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "File exceeds the 2 MB limit");
        }

        // Use only the original filename as metadata — never for any filesystem operation.
        String originalFilename = file.getOriginalFilename() != null
                ? file.getOriginalFilename()
                : "unknown";

        String rawText;
        try {
            rawText = new String(file.getBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("Failed to read uploaded file bytes", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file content");
        }

        Document document = Document.builder()
                .filename(originalFilename)
                .contentType(contentType)
                .sizeBytes(file.getSize())
                .rawText(rawText)
                .status(Document.DocumentStatus.PENDING_INGEST)
                .build();

        Document saved = documentRepository.save(document);
        log.debug("Persisted document id={} filename={} size={}", saved.getId(), saved.getFilename(), saved.getSizeBytes());

        return DocumentDto.from(saved);
    }

    /**
     * Strips any parameters (e.g., {@code ; charset=UTF-8}) from the declared content-type
     * and lower-cases it for consistent comparison.
     */
    private static String normalizeContentType(String raw) {
        if (raw == null) {
            return "";
        }
        int semicolon = raw.indexOf(';');
        return (semicolon >= 0 ? raw.substring(0, semicolon) : raw).trim().toLowerCase();
    }
}
