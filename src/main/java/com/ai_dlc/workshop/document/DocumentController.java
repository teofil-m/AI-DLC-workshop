package com.ai_dlc.workshop.document;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for document upload operations.
 * Thin adapter layer — all logic is delegated to {@link DocumentService}.
 */
@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
@Slf4j
public class DocumentController {

    private final DocumentService documentService;

    /**
     * Upload a document file and trigger RAG ingestion.
     *
     * <p>Accepts {@code multipart/form-data} with a single {@code file} part.
     * The endpoint is protected — callers must supply a valid Bearer JWT token.
     * The {@code sub} claim is extracted from the token and propagated to the
     * ingestion pipeline as user-scoped metadata on every chunk.
     *
     * @param file the uploaded file
     * @param jwt  the validated JWT from the OAuth2 resource server filter
     * @return 201 Created with a {@link DocumentDto} body
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentDto> upload(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getSubject();
        if (userId == null || userId.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "JWT must contain a sub claim");
        }
        // Log the sanitised filename only — raw client values may contain log-injection characters
        log.debug("Received document upload request, originalFilename={}", DocumentService.sanitiseFilename(file.getOriginalFilename()));
        DocumentDto dto = documentService.upload(file, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
}
