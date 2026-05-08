package com.ai_dlc.workshop.document;

import java.time.Instant;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity representing an uploaded document awaiting or having completed RAG ingestion.
 * equals/hashCode keyed on id only — avoids the Hibernate @Data anti-pattern.
 */
@Entity
@Table(name = "documents")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
public class Document {

    public enum DocumentStatus {
        PENDING_INGEST,
        INGESTING,
        INGESTED,
        INGEST_FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @EqualsAndHashCode.Include
    private UUID id;

    @Column(nullable = false)
    private String filename;

    @Column(nullable = false)
    private String contentType;

    private long sizeBytes;

    @Column(columnDefinition = "TEXT")
    private String rawText;

    @CreationTimestamp
    private Instant createdAt;

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status = DocumentStatus.PENDING_INGEST;
}
