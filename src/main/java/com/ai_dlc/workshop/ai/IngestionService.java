package com.ai_dlc.workshop.ai;

import java.util.List;
import java.util.Map;

import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import com.ai_dlc.workshop.common.MetadataKeys;
import com.ai_dlc.workshop.document.Document;
import com.ai_dlc.workshop.document.DocumentRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Orchestrates the RAG ingestion pipeline for a single document.
 *
 * <p>Pipeline steps:
 * <ol>
 *   <li>Mark the document {@code INGESTING} and flush the status change immediately.</li>
 *   <li>Wrap {@code rawText} in a Spring AI {@link org.springframework.ai.document.Document}
 *       with canonical metadata.</li>
 *   <li>Split into token-bounded chunks via {@link TokenTextSplitter}.</li>
 *   <li>Tag each chunk with its ordinal {@code chunkIndex}.</li>
 *   <li>Write all chunks to the {@link VectorStore} — embeddings are generated internally
 *       by the vector store's configured {@code EmbeddingModel}.</li>
 *   <li>Mark the document {@code INGESTED} on success, or {@code INGEST_FAILED} on error.</li>
 * </ol>
 *
 * <p><b>NOT {@code @Transactional}:</b> status updates must commit immediately even when
 * a later step fails, so that callers can observe the correct terminal status.
 *
 * <p>Token cost: every call to {@link VectorStore#add} triggers one embedding API call
 * per chunk. See {@link AiConfig} for a cost estimate.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class IngestionService {

    private final VectorStore vectorStore;
    private final TokenTextSplitter textSplitter;
    private final DocumentRepository documentRepository;

    /**
     * Ingests the given document into the vector store.
     *
     * @param document the persisted {@link Document} entity (must have a non-null {@code rawText})
     * @param userId   the {@code sub} claim from the caller's JWT — stored as metadata on every chunk
     * @throws RuntimeException if any ingestion step fails (after marking the document
     *                          {@code INGEST_FAILED} and persisting that status)
     */
    public void ingest(Document document, String userId) {
        // Step 1 — mark INGESTING so the caller sees progress immediately
        document.setStatus(Document.DocumentStatus.INGESTING);
        documentRepository.save(document);

        try {
            // Step 2 — build a Spring AI document with canonical metadata
            org.springframework.ai.document.Document aiDoc =
                    new org.springframework.ai.document.Document(
                            document.getRawText(),
                            Map.of(
                                    MetadataKeys.USER_ID, userId,
                                    MetadataKeys.DOC_ID, document.getId().toString(),
                                    MetadataKeys.SOURCE, document.getFilename()));

            // Step 3 — split into token-bounded chunks
            List<org.springframework.ai.document.Document> chunks = textSplitter.apply(List.of(aiDoc));

            // Step 4 — tag each chunk with its ordinal index for ordered retrieval
            for (int i = 0; i < chunks.size(); i++) {
                chunks.get(i).getMetadata().put(MetadataKeys.CHUNK_INDEX, String.valueOf(i));
            }

            // Step 5 — write to the vector store; embeddings are generated internally
            vectorStore.add(chunks);

            // Step 6 — mark INGESTED on success
            document.setStatus(Document.DocumentStatus.INGESTED);
            documentRepository.save(document);
            log.info("Ingested document id={} into {} chunks", document.getId(), chunks.size());

        } catch (Exception e) {
            log.error("Ingestion failed for document id={}", document.getId(), e);
            document.setStatus(Document.DocumentStatus.INGEST_FAILED);
            document.setErrorMessage(e.getMessage());
            documentRepository.save(document);
            throw new RuntimeException("Ingestion failed for document " + document.getId(), e);
        }
    }
}
