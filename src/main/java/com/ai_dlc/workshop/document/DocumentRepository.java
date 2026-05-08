package com.ai_dlc.workshop.document;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data JPA repository for {@link Document} entities.
 */
public interface DocumentRepository extends JpaRepository<Document, UUID> {
}
