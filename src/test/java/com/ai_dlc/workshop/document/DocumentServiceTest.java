package com.ai_dlc.workshop.document;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doNothing;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.ai_dlc.workshop.ai.IngestionService;
import com.ai_dlc.workshop.document.Document.DocumentStatus;

/**
 * Plain unit test for {@link DocumentService} — no Spring context.
 * The {@link DocumentRepository} and {@link IngestionService} are mocked via Mockito.
 */
@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    private static final String TEST_USER_ID = "user-123";

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private IngestionService ingestionService;

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(documentRepository, ingestionService);
    }

    // ---------------------------------------------------------------------------
    // Happy-path
    // ---------------------------------------------------------------------------

    @Test
    void upload_validTxt_savesEntityAndReturnsDtoWithPendingStatus() {
        // GIVEN a valid text/plain file with content
        MockMultipartFile file = new MockMultipartFile(
                "file", "doc.txt", "text/plain", "hello".getBytes());

        Document savedDocument = Document.builder()
                .id(UUID.randomUUID())
                .filename("doc.txt")
                .contentType("text/plain")
                .sizeBytes(5L)
                .rawText("hello")
                .status(DocumentStatus.PENDING_INGEST)
                .build();
        // set createdAt manually because @CreationTimestamp only fires via Hibernate in real persistence
        savedDocument.setCreatedAt(Instant.now());

        given(documentRepository.save(any(Document.class))).willReturn(savedDocument);
        doNothing().when(ingestionService).ingest(any(Document.class), any(String.class));

        // WHEN upload is called
        DocumentDto result = documentService.upload(file, TEST_USER_ID);

        // THEN the returned DTO reflects PENDING_INGEST status and correct filename
        assertThat(result.filename()).isEqualTo("doc.txt");
        assertThat(result.status()).isEqualTo("PENDING_INGEST");
        assertThat(result.id()).isNotNull();
    }

    // ---------------------------------------------------------------------------
    // Validation failures
    // ---------------------------------------------------------------------------

    @Test
    void upload_pdfMimeType_throwsUnsupportedMediaType() {
        // GIVEN a file with application/pdf content type (not in the allowed set)
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.pdf", "application/pdf", "%PDF-1.4 binary".getBytes());

        // WHEN upload is called
        // THEN a ResponseStatusException with 415 is thrown
        assertThatThrownBy(() -> documentService.upload(file, TEST_USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value())
                            .isEqualTo(HttpStatus.UNSUPPORTED_MEDIA_TYPE.value());
                });
    }

    @Test
    void upload_oversizeFile_throwsPayloadTooLarge() {
        // GIVEN a byte array that exceeds the 2 MB limit by 1 byte
        byte[] oversizeContent = new byte[2 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.txt", "text/plain", oversizeContent);

        // WHEN upload is called
        // THEN a ResponseStatusException with 413 is thrown
        assertThatThrownBy(() -> documentService.upload(file, TEST_USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value())
                            .isEqualTo(HttpStatus.PAYLOAD_TOO_LARGE.value());
                });
    }

    @Test
    void upload_emptyFile_throwsBadRequest() {
        // GIVEN a zero-length multipart file
        MockMultipartFile file = new MockMultipartFile(
                "file", "empty.txt", "text/plain", new byte[0]);

        // WHEN upload is called
        // THEN a ResponseStatusException with 400 is thrown
        assertThatThrownBy(() -> documentService.upload(file, TEST_USER_ID))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(ex -> {
                    ResponseStatusException rse = (ResponseStatusException) ex;
                    assertThat(rse.getStatusCode().value())
                            .isEqualTo(HttpStatus.BAD_REQUEST.value());
                });
    }
}
