package com.ai_dlc.workshop.document;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

/**
 * Slice test for {@link DocumentController} — verifies HTTP contract without
 * starting a full application context.
 */
@WebMvcTest(DocumentController.class)
@ActiveProfiles("test")
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentService documentService;

    // ---------------------------------------------------------------------------
    // Happy-path
    // ---------------------------------------------------------------------------

    @Test
    @WithMockUser
    void upload_validTxtFile_returns201WithDto() throws Exception {
        // GIVEN a valid text/plain file under 2 MB and a mocked service returning PENDING_INGEST
        UUID expectedId = UUID.randomUUID();
        DocumentDto dto = new DocumentDto(
                expectedId, "test.txt", "text/plain", 100L, "PENDING_INGEST", Instant.now());
        given(documentService.upload(any())).willReturn(dto);

        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "hello world".getBytes());

        // WHEN POST /api/documents with valid multipart
        // THEN 201 Created with body containing id and status = PENDING_INGEST
        mockMvc.perform(multipart("/api/documents").file(file).with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(expectedId.toString()))
                .andExpect(jsonPath("$.status").value("PENDING_INGEST"))
                .andExpect(jsonPath("$.filename").value("test.txt"));
    }

    // ---------------------------------------------------------------------------
    // Validation failures — delegated to service layer
    // ---------------------------------------------------------------------------

    @Test
    @WithMockUser
    void upload_invalidMimeType_returns415() throws Exception {
        // GIVEN service throws 415 for application/pdf content type
        given(documentService.upload(any()))
                .willThrow(new ResponseStatusException(HttpStatus.UNSUPPORTED_MEDIA_TYPE));

        MockMultipartFile file = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", "%PDF-1.4 content".getBytes());

        // WHEN POST /api/documents with a PDF file
        // THEN 415 Unsupported Media Type
        mockMvc.perform(multipart("/api/documents").file(file).with(csrf()))
                .andExpect(status().isUnsupportedMediaType());
    }

    @Test
    @WithMockUser
    void upload_oversizeFile_returns413() throws Exception {
        // GIVEN service throws 413 for a file exceeding 2 MB
        given(documentService.upload(any()))
                .willThrow(new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE));

        byte[] oversizeBytes = new byte[2 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.txt", "text/plain", oversizeBytes);

        // WHEN POST /api/documents with an oversize file
        // THEN 413 Payload Too Large
        mockMvc.perform(multipart("/api/documents").file(file).with(csrf()))
                .andExpect(status().isPayloadTooLarge());
    }

    @Test
    @WithMockUser
    void upload_emptyFile_returns400() throws Exception {
        // GIVEN service throws 400 for an empty file
        given(documentService.upload(any()))
                .willThrow(new ResponseStatusException(HttpStatus.BAD_REQUEST));

        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]);

        // WHEN POST /api/documents with a zero-byte file
        // THEN 400 Bad Request
        mockMvc.perform(multipart("/api/documents").file(file).with(csrf()))
                .andExpect(status().isBadRequest());
    }

    // ---------------------------------------------------------------------------
    // Security — unauthenticated requests must be rejected
    // ---------------------------------------------------------------------------

    @Test
    void upload_noAuthentication_returns401or403() throws Exception {
        // GIVEN no @WithMockUser — request is anonymous
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.txt", "text/plain", "hello".getBytes());

        // WHEN POST /api/documents without a valid authentication token
        // THEN 401 Unauthorized or 403 Forbidden
        mockMvc.perform(multipart("/api/documents").file(file).with(csrf()))
                .andExpect(status().is(Matchers.either(Matchers.is(401)).or(Matchers.is(403))));
    }
}
