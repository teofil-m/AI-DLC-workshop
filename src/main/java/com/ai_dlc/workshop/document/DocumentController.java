package com.ai_dlc.workshop.document;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
     * Upload a document file.
     *
     * <p>Accepts {@code multipart/form-data} with a single {@code file} part.
     * The endpoint is protected — callers must supply a valid Bearer token.
     *
     * @param file the uploaded file
     * @return 201 Created with a {@link DocumentDto} body
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<DocumentDto> upload(@RequestParam("file") MultipartFile file) {
        log.debug("Received document upload request, originalFilename={}", file.getOriginalFilename());
        DocumentDto dto = documentService.upload(file);
        return ResponseEntity.status(HttpStatus.CREATED).body(dto);
    }
}
