package com.yu.transferrag.service;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvidenceLegacyVerifierTest {
    @TempDir Path root;
    QdrantMetadataReader reader = mock(QdrantMetadataReader.class);
    EvidenceChunkPersistenceService.Snapshot snapshot() throws Exception {
        Path path = root.resolve("test.md"); Files.writeString(path,"test evidence");
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
        var vector = org.springframework.ai.document.Document.builder().text("test").metadata("documentId","2")
            .metadata("chunkId","3").metadata("chunkIndex",0).metadata("documentRole","EVIDENCE").build();
        return new EvidenceChunkPersistenceService.Snapshot(2,path.toString(),sha,List.of(vector));
    }
    @Test void consistentLegacyPayloadPassesWithoutWrites() throws Exception {
        var snapshot = snapshot();
        when(reader.findByDocumentId(2L)).thenReturn(List.of(new QdrantMetadataReader.QdrantMetadataPoint("legacy",
            Map.of("documentId","2","chunkId","3","chunkIndex",0))));
        assertDoesNotThrow(() -> new EvidenceLegacyVerifier(reader,root.toString()).verify(snapshot));
        verify(reader).findByDocumentId(2L); verifyNoMoreInteractions(reader);
    }
    @Test void missingIndexNeedsReviewNotAutomaticReimport() throws Exception {
        var snapshot = snapshot();
        when(reader.findByDocumentId(2L)).thenReturn(List.of());
        var failure = assertThrows(EvidenceImportProcessor.StageFailure.class, () -> new EvidenceLegacyVerifier(reader,root.toString()).verify(snapshot));
        assertFalse(failure.retryable());
    }
    @Test void mismatchedHashFailsBeforeNetwork() throws Exception {
        var s = snapshot(); var changed = new EvidenceChunkPersistenceService.Snapshot(2,s.filePath(),"bad",s.vectors());
        assertThrows(EvidenceImportProcessor.StageFailure.class, () -> new EvidenceLegacyVerifier(reader,root.toString()).verify(changed));
        verifyNoInteractions(reader);
    }
}
