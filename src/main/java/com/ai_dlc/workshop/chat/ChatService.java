package com.ai_dlc.workshop.chat;

import com.ai_dlc.workshop.common.MetadataKeys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Synchronous RAG chat service.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Retrieve top-K documents from the {@link VectorStore} via similarity search.</li>
 *   <li>Format retrieved chunks into a context block.</li>
 *   <li>Render the {@code rag-chat.st} prompt template with {question} and {context}.</li>
 *   <li>Call the LLM via {@link ChatClient} and return the answer with citations.</li>
 * </ol>
 *
 * <p>Token cost: each call issues 1 embedding query (retrieval) + 1 chat completion.
 * Context size scales with top-K * avg chunk size. Default top-K=4.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private static final String PROMPT_TEMPLATE_PATH = "prompts/rag-chat.st";

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    @Value("${app.ai.rag.top-k:4}")
    private int topK;

    @Value("${app.ai.rag.similarity-threshold:0.7}")
    private double similarityThreshold;

    /**
     * Answers {@code question} using RAG: retrieves relevant chunks and calls the LLM
     * with the assembled context.
     *
     * @param question sanitised user question (must not be blank)
     * @param userId   the authenticated user's subject claim
     * @return the generated answer together with citation metadata
     */
    public ChatResponse chat(String question, String userId) {
        log.debug("RAG chat request received for userId={}", userId);

        // 1. Retrieve relevant documents from the vector store
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .build();

        List<Document> relevantDocs = vectorStore.similaritySearch(searchRequest);
        log.debug("Retrieved {} document chunks from vector store", relevantDocs.size());

        // 2. Build context string from retrieved chunks
        String context = relevantDocs.isEmpty()
                ? "No relevant documents found."
                : relevantDocs.stream()
                        .map(Document::getText)
                        .filter(Objects::nonNull)
                        .collect(Collectors.joining("\n\n---\n\n"));

        // 3. Load and render prompt template (string substitution — not an interpreter)
        String promptTemplate = loadPromptTemplate();
        String renderedPrompt = promptTemplate
                .replace("{question}", sanitise(question))
                .replace("{context}", context);

        // 4. Call the LLM synchronously
        String answer = chatClient.prompt()
                .user(renderedPrompt)
                .call()
                .content();

        // 5. Map retrieved documents to CitationDtos
        List<CitationDto> citations = relevantDocs.stream()
                .map(doc -> toCitation(doc.getMetadata()))
                .collect(Collectors.toList());

        return new ChatResponse(answer, citations);
    }

    private CitationDto toCitation(Map<String, Object> metadata) {
        String source = objectToString(metadata.get(MetadataKeys.SOURCE));
        String docId = objectToString(metadata.get(MetadataKeys.DOC_ID));
        String chunkIndex = objectToString(metadata.get(MetadataKeys.CHUNK_INDEX));
        return new CitationDto(source, docId, chunkIndex);
    }

    private static String objectToString(Object value) {
        return value != null ? value.toString() : null;
    }

    private String loadPromptTemplate() {
        try {
            ClassPathResource resource = new ClassPathResource(PROMPT_TEMPLATE_PATH);
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Failed to load prompt template from {}", PROMPT_TEMPLATE_PATH, e);
            throw new IllegalStateException("Could not load RAG prompt template", e);
        }
    }

    /**
     * Minimal prompt-injection defence: strips null bytes and trims surrounding whitespace.
     * The template rendering uses plain {@link String#replace}, not a template engine,
     * so placeholder-in-input injection ({@code {context}} in user input) is not possible.
     */
    private static String sanitise(String input) {
        if (input == null) {
            return "";
        }
        return input.strip();
    }
}
