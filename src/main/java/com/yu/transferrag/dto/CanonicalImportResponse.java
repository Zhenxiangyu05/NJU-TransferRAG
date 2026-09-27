package com.yu.transferrag.dto;

import com.yu.transferrag.entity.DocumentRole;

public record CanonicalImportResponse(
        Long documentId,
        DocumentRole documentRole,
        String sourceType,
        int chunkCount,
        int evidenceRefCount,
        int indexedChunks
) {
}
