ALTER TABLE documents
    ADD COLUMN IF NOT EXISTS error_message TEXT;

CREATE INDEX IF NOT EXISTS idx_documents_created_at ON documents (created_at DESC);
