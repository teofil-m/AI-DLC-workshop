package com.ai_dlc.workshop.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;

import com.ai_dlc.workshop.common.MetadataKeys;
import com.ai_dlc.workshop.document.Document;
import com.ai_dlc.workshop.document.Document.DocumentStatus;
import com.ai_dlc.workshop.document.DocumentRepository;

@ExtendWith(MockitoExtension.class)
class IngestionServiceTest {

    @Mock
    VectorStore vectorStore;

    @Mock
    DocumentRepository documentRepository;

    // Real splitter — exercises actual chunking behaviour rather than mocking it away
    TokenTextSplitter splitter = TokenTextSplitter.builder()
            .withChunkSize(100)
            .withMinChunkSizeChars(20)
            .withMinChunkLengthToEmbed(5)
            .withMaxNumChunks(10000)
            .withKeepSeparator(true)
            .build();

    IngestionService ingestionService;

    @BeforeEach
    void setUp() {
        ingestionService = new IngestionService(vectorStore, splitter, documentRepository);
    }

    private Document documentWithText(String text) {
        Document doc = Document.builder()
                .id(UUID.randomUUID())
                .filename("test.txt")
                .contentType("text/plain")
                .sizeBytes(text.length())
                .rawText(text)
                .build(); // status defaults to PENDING_INGEST via @Builder.Default
        return doc;
    }

    @Test
    void ingest_validDocument_setsIngestingThenIngestedStatus() {
        Document document = documentWithText("Hello world. ".repeat(50));

        // Capture status at each save() — ArgumentCaptor captures a reference, not a snapshot
        List<DocumentStatus> savedStatuses = new ArrayList<>();
        doAnswer(inv -> {
            savedStatuses.add(((Document) inv.getArgument(0)).getStatus());
            return inv.getArgument(0);
        }).when(documentRepository).save(any(Document.class));

        ingestionService.ingest(document, "user-123");

        assertThat(savedStatuses).hasSize(2);
        assertThat(savedStatuses.get(0)).isEqualTo(DocumentStatus.INGESTING);
        assertThat(savedStatuses.get(1)).isEqualTo(DocumentStatus.INGESTED);
    }

    @Test
    void ingest_validDocument_callsVectorStoreWithAtLeastOneChunk() {
        Document document = documentWithText("Hello world. ".repeat(50));

        ingestionService.ingest(document, "user-123");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<org.springframework.ai.document.Document>> chunksCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(chunksCaptor.capture());
        assertThat(chunksCaptor.getValue()).isNotEmpty();
    }

    @Test
    void ingest_validDocument_chunksCarryUserIdMetadata() {
        Document document = documentWithText("Hello world. ".repeat(50));

        ingestionService.ingest(document, "user-123");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<org.springframework.ai.document.Document>> chunksCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(chunksCaptor.capture());

        assertThat(chunksCaptor.getValue()).allSatisfy(chunk ->
                assertThat(chunk.getMetadata())
                        .containsEntry(MetadataKeys.USER_ID, "user-123"));
    }

    @Test
    void ingest_validDocument_chunksCarryDocIdMetadata() {
        Document document = documentWithText("Hello world. ".repeat(50));

        ingestionService.ingest(document, "user-123");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<org.springframework.ai.document.Document>> chunksCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(chunksCaptor.capture());

        assertThat(chunksCaptor.getValue()).allSatisfy(chunk ->
                assertThat(chunk.getMetadata())
                        .containsEntry(MetadataKeys.DOC_ID, document.getId().toString()));
    }

    @Test
    void ingest_validDocument_chunksHaveSequentialChunkIndexMetadata() {
        Document document = documentWithText("Hello world. ".repeat(50));

        ingestionService.ingest(document, "user-123");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<org.springframework.ai.document.Document>> chunksCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(chunksCaptor.capture());

        List<org.springframework.ai.document.Document> chunks = chunksCaptor.getValue();
        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).getMetadata())
                    .containsEntry(MetadataKeys.CHUNK_INDEX, String.valueOf(i));
        }
    }

    @Test
    void ingest_vectorStoreThrows_setsIngestFailedStatus() {
        Document document = documentWithText("Hello world. ".repeat(50));
        doThrow(new RuntimeException("Azure down")).when(vectorStore).add(any());

        List<DocumentStatus> savedStatuses = new ArrayList<>();
        doAnswer(inv -> {
            savedStatuses.add(((Document) inv.getArgument(0)).getStatus());
            return inv.getArgument(0);
        }).when(documentRepository).save(any(Document.class));

        assertThatThrownBy(() -> ingestionService.ingest(document, "user-123"))
                .isInstanceOf(RuntimeException.class);

        assertThat(savedStatuses).isNotEmpty();
        assertThat(savedStatuses.getLast()).isEqualTo(DocumentStatus.INGEST_FAILED);
    }

    @Test
    void ingest_emptyText_stillCompletesWithIngestedStatus() {
        Document document = documentWithText("");

        List<DocumentStatus> savedStatuses = new ArrayList<>();
        doAnswer(inv -> {
            savedStatuses.add(((Document) inv.getArgument(0)).getStatus());
            return inv.getArgument(0);
        }).when(documentRepository).save(any(Document.class));

        ingestionService.ingest(document, "user-123");

        assertThat(savedStatuses).isNotEmpty();
        assertThat(savedStatuses.getLast()).isEqualTo(DocumentStatus.INGESTED);
    }
}
