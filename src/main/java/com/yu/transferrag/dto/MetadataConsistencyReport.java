package com.yu.transferrag.dto;

import java.util.List;
import java.util.Map;

public record MetadataConsistencyReport(
        String status,
        Long documentId,
        Map<String, Object> mysqlDocument,
        int mysqlChunkCount,
        int qdrantPointCount,
        List<String> issues,
        List<String> warnings,
        List<ChunkConsistency> chunks
) {

    public record ChunkConsistency(
            Long chunkId,
            Integer chunkIndex,
            Map<String, Object> mysqlChunk,
            List<Map<String, Object>> qdrantPoints,
            List<String> issues,
            List<String> warnings
    ) {
    }
}
