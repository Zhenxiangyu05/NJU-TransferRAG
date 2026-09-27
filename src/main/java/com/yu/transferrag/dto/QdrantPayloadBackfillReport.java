package com.yu.transferrag.dto;

import java.util.List;

public record QdrantPayloadBackfillReport(
        boolean dryRun,
        int scannedPoints,
        int requiresUpdate,
        int updatedPoints,
        int alreadyCorrect,
        int missingChunks,
        int canonicalPreserved,
        int errors,
        List<String> warnings
) {
}
