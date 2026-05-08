package com.ai_dlc.workshop.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

import com.ai_dlc.workshop.document.Document.DocumentStatus;

/**
 * Repository slice test for {@link DocumentRepository}.
 * Uses H2 in-memory with ddl-auto=create-drop so Hibernate owns the schema.
 * Flyway is suppressed — it runs against the real DB only.
 */
@DataJpaTest
@ActiveProfiles("test")
// @DataJpaTest ignores the test profile's flyway/ddl-auto — override explicitly.
@TestPropertySource(properties = {
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=create-drop"
})
class DocumentRepositoryTest {

    @Autowired
    private TestEntityManager em;

    @Autowired
    private DocumentRepository documentRepository;

    @Test
    void save_newDocument_persistsWithPendingIngestStatus() {
        // GIVEN
        Document document = Document.builder()
                .filename("report.txt")
                .contentType("text/plain")
                .sizeBytes(512L)
                .rawText("Sample content")
                .build();

        // WHEN — persistFlushFind: persist → flush to DB → clear 1st-level cache → SELECT
        // This forces @CreationTimestamp to be written and re-read from DB.
        Document found = em.persistFlushFind(document);

        // THEN
        assertThat(found.getId()).isNotNull();
        assertThat(found.getFilename()).isEqualTo("report.txt");
        assertThat(found.getStatus()).isEqualTo(DocumentStatus.PENDING_INGEST);
        assertThat(found.getCreatedAt()).isNotNull();
    }

    @Test
    void findAll_empty_returnsEmptyList() {
        // GIVEN an empty repository
        // WHEN
        List<Document> documents = documentRepository.findAll();
        // THEN
        assertThat(documents).isEmpty();
    }
}
