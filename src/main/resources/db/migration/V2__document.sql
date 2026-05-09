CREATE TABLE documents (
    id           UUID                     NOT NULL PRIMARY KEY,
    filename     VARCHAR(255)             NOT NULL,
    content_type VARCHAR(100)             NOT NULL,
    size_bytes   BIGINT                   NOT NULL,
    raw_text     TEXT,
    created_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now(),
    status       VARCHAR(30)              NOT NULL DEFAULT 'PENDING_INGEST'
);

CREATE INDEX idx_documents_status ON documents (status);
