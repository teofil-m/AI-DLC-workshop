package com.ai_dlc.workshop.common;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MetadataKeysTest {

    @Test
    void userIdKeyValue() {
        // GIVEN / WHEN / THEN — constant must equal the canonical key string
        assertEquals("userId", MetadataKeys.USER_ID);
    }

    @Test
    void docIdKeyValue() {
        assertEquals("docId", MetadataKeys.DOC_ID);
    }

    @Test
    void sourceKeyValue() {
        assertEquals("source", MetadataKeys.SOURCE);
    }

    @Test
    void chunkIndexKeyValue() {
        assertEquals("chunkIndex", MetadataKeys.CHUNK_INDEX);
    }
}
