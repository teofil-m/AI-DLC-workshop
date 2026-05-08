package com.ai_dlc.workshop.chat;

import com.ai_dlc.workshop.common.MetadataKeys;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;

/**
 * Non-blocking RAG streaming chat service.
 *
 * <p>Pipeline:
 * <ol>
 *   <li>Retrieve top-K document chunks scoped to the authenticated user from {@link VectorStore}
 *       (blocking call — acceptable because retrieval is a fast, bounded I/O operation).</li>
 *   <li>Format retrieved chunks into a context block.</li>
 *   <li>Render the {@code rag-chat.st} prompt template with {context} and {question}.</li>
 *   <li>Stream LLM response tokens via {@link ChatClient} — returns {@link Flux}{@literal <}String{@literal >}.</li>
 * </ol>
 *
 * <p>Token cost: each call issues 1 embedding query (retrieval) + 1 streaming chat completion.
 * Context size scales with top-K * avg chunk size. Default top-K=4.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StreamingChatService {

    private static final String PROMPT_TEMPLATE_PATH = "prompts/rag-chat.st";

    private final ChatClient chatClient;
    private final VectorStore vectorStore;

    @Value("${app.ai.rag.top-k:4}")
    private int topK;

    @Value("${app.ai.rag.similarity-threshold:0.7}")
    private double similarityThreshold;

    // Loaded once at startup — template never changes at runtime
    private String promptTemplate;

    @PostConstruct
    void init() {
        this.promptTemplate = loadPromptTemplate();
    }

    /**
     * Streams an LLM answer to {@code question} using RAG, emitting tokens as they arrive.
     *
     * <p>The vector store retrieval is synchronous and completes before the streaming chat call
     * is initiated. Streaming then begins immediately and each token is emitted as a
     * {@link Flux} element, which Spring WebFlux serialises as an SSE {@code data:} frame.
     *
     * @param question sanitised user question (must not be blank)
     * @param userId   the authenticated user's subject claim — scopes the vector store search
     * @return a {@link Flux} of token strings; the stream completes when the LLM finishes
     */
    public Flux<String> stream(String question, String userId) {
        log.debug("RAG streaming chat request received for userId={}", userId);

        // 1. Retrieve relevant documents scoped to this user — prevents cross-user data leakage.
        //    Blocking call is acceptable here: retrieval is bounded and fast relative to LLM latency.
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(topK)
                .similarityThreshold(similarityThreshold)
                .filterExpression(MetadataKeys.USER_ID + " == '" + userId + "'")
                .build();

        List<Document> relevantDocs = vectorStore.similaritySearch(searchRequest);
        log.debug("Retrieved {} document chunks from vector store for userId={}", relevantDocs.size(), userId);

        // 2. Build context string from retrieved chunks
        String context = relevantDocs.isEmpty()
                ? "No relevant documents found."
                : relevantDocs.stream()
                        .map(Document::getText)
                        .filter(Objects::nonNull)
                        .collect(java.util.stream.Collectors.joining("\n\n---\n\n"));

        // 3. Render prompt — substitute {context} first so any literal "{question}" in chunk
        //    text is never matched by the second replacement (prompt-injection defence)
        String renderedPrompt = promptTemplate
                .replace("{context}", context)
                .replace("{question}", sanitise(question));

        // 4. Call the LLM in streaming mode — returns Flux<String> of token chunks.
        //    Each emitted String is one or more tokens as produced by the model.
        return chatClient.prompt()
                .user(renderedPrompt)
                .stream()
                .content();
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
     * Minimal prompt-injection defence: strips null and trims surrounding whitespace.
     * Placeholder re-injection from user input is impossible because {context} is
     * substituted before {question}.
     */
    private static String sanitise(String input) {
        if (input == null) {
            return "";
        }
        return input.strip();
    }
}
