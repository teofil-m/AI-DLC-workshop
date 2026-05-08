package com.ai_dlc.workshop.document;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import com.ai_dlc.workshop.document.Document.DocumentStatus;

/**
 * Repository slice test for {@link DocumentRepository}.
 * Uses an H2 in-memory database with {@code ddl-auto: create-drop}.
 * Flyway is disabled in the test profile — Hibernate manages the schema directly.
 */
@DataJpaTest
@ActiveProfiles("test")
class DocumentRepositoryTest {

    @Autowired
    private DocumentRepository documentRepository;

    @Test
    void save_newDocument_persistsWithPendingIngestStatus() {
        // GIVEN a new Document entity built with builder defaults
        Document document = Document.builder()
                .filename("report.txt")
                .contentType("text/plain")
                .sizeBytes(512L)
                .rawText("Sample content")
                .build();

        // WHEN the entity is saved and then reloaded by its generated id
        Document saved = documentRepository.save(document);
        Optional<Document> reloaded = documentRepository.findById(saved.getId());

        // THEN the reloaded entity has the expected field values and default status
        assertThat(reloaded).isPresent();
        Document found = reloaded.get();
        assertThat(found.getFilename()).isEqualTo("report.txt");
        assertThat(found.getStatus()).isEqualTo(DocumentStatus.PENDING_INGEST);
        assertThat(found.getCreatedAt()).isNotNull();
        assertThat(found.getId()).isNotNull();
    }

    @Test
    void findAll_empty_returnsEmptyList() {
        // GIVEN the repository contains no documents (fresh transaction-scoped H2 db)
        // WHEN findAll is called
        List<Document> documents = documentRepository.findAll();

        // THEN an empty list is returned
        assertThat(documents).isEmpty();
    }
}
