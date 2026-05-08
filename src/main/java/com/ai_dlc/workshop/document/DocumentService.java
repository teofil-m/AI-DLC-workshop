package com.ai_dlc.workshop.document;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.ai_dlc.workshop.ai.IngestionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Service encapsulating all business logic for document upload, persistence,
 * and triggering RAG ingestion via {@link IngestionService}.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DocumentService {

    private static final long MAX_SIZE_BYTES = 2L * 1024 * 1024; // 2 MB
    private static final Set<String> ALLOWED_CONTENT_TYPES = Set.of("text/plain", "text/markdown");
    // Allow only safe filename characters; strip path separators and control chars
    private static final Pattern SAFE_FILENAME = Pattern.compile("[^a-zA-Z0-9.\\-_ ]");

    private final DocumentRepository documentRepository;
    private final IngestionService ingestionService;

    /**
     * Validates, persists, and ingests an uploaded file.
     *
     * <p>Validation order: empty → MIME type → declared size (cheapest checks first).
     * Actual byte length is re-checked after reading to catch Content-Length spoofing.
     *
     * <p>After the document is saved with {@code PENDING_INGEST}, the {@link IngestionService}
     * is called synchronously to chunk, embed, and write to the vector store.
     * The {@code @Transactional} boundary ensures the document row is visible to the
     * ingestion status updates; if ingestion fails the document is saved with
     * {@code INGEST_FAILED} (not rolled back).
     *
     * @param file   the uploaded multipart file
     * @param userId the {@code sub} claim from the caller's JWT — propagated to chunk metadata
     * @return a {@link DocumentDto} reflecting the final ingestion status
     */
    @Transactional
    public DocumentDto upload(MultipartFile file, String userId) {
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
            log.warn("Rejected upload of {} bytes (declared limit {} bytes)", file.getSize(), MAX_SIZE_BYTES);
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the 2 MB limit");
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            log.error("Failed to read uploaded file bytes", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to read file content");
        }

        // Re-check actual byte length — guards against Content-Length header spoofing
        if (bytes.length > MAX_SIZE_BYTES) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "File exceeds the 2 MB limit");
        }

        String rawText = new String(bytes, StandardCharsets.UTF_8);
        String safeFilename = sanitiseFilename(file.getOriginalFilename());
        log.debug("Persisting document filename={} contentType={} size={}", safeFilename, contentType, bytes.length);

        Document document = Document.builder()
                .filename(safeFilename)
                .contentType(contentType)
                .sizeBytes(bytes.length)
                .rawText(rawText)
                .build(); // status defaults to PENDING_INGEST via @Builder.Default

        Document saved = documentRepository.save(document);

        // Trigger synchronous ingestion — status transitions are managed inside IngestionService
        ingestionService.ingest(saved, userId);

        return DocumentDto.from(saved);
    }

    /**
     * Strips path separators, null bytes, and special characters from the client-supplied filename.
     * The result is safe to store as metadata and render in UI without XSS risk.
     */
    static String sanitiseFilename(String raw) {
        if (raw == null || raw.isBlank()) {
            return "unknown";
        }
        // Take only the final path component (strip any directory traversal)
        String basename = raw.replace('\\', '/');
        int slash = basename.lastIndexOf('/');
        if (slash >= 0) {
            basename = basename.substring(slash + 1);
        }
        // Replace unsafe characters with underscores
        String safe = SAFE_FILENAME.matcher(basename).replaceAll("_");
        // Truncate to DB column size
        if (safe.length() > 255) {
            safe = safe.substring(0, 255);
        }
        return safe.isBlank() ? "unknown" : safe;
    }

    private static String normalizeContentType(String raw) {
        if (raw == null) return "";
        int semicolon = raw.indexOf(';');
        return (semicolon >= 0 ? raw.substring(0, semicolon) : raw).trim().toLowerCase();
    }
}
