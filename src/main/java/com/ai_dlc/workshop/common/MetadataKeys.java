package com.ai_dlc.workshop.common;

/**
 * Canonical metadata key constants used across vector store documents and RAG pipelines.
 */
public final class MetadataKeys {

    public static final String USER_ID     = "userId";
    public static final String DOC_ID      = "docId";
    public static final String SOURCE      = "source";
    public static final String CHUNK_INDEX = "chunkIndex";

    private MetadataKeys() {}
}
