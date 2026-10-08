package com.yu.transferrag.service;

import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.document.Document;
import com.yu.transferrag.repository.ChunkRepository;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvidenceIndexIdempotencyTest {
    @Test void stablePointIdsDifferByDocumentAndChunk() {
        String id = EvidenceChunkPersistenceService.pointId(1,2);
        assertEquals(id,EvidenceChunkPersistenceService.pointId(1,2));
        assertNotEquals(id,EvidenceChunkPersistenceService.pointId(1,3));
        assertNotEquals(id,EvidenceChunkPersistenceService.pointId(2,2));
        assertDoesNotThrow(() -> UUID.fromString(id));
    }
    @Test void partialFailureThenRetryDoesNotDeleteOrIncreasePointCount() {
        var store = mock(VectorStore.class);
        Map<String,Document> simulatedPoints = new HashMap<>();
        int[] calls = {0};
        doAnswer(call -> {
            List<Document> docs = call.getArgument(0);
            simulatedPoints.put(docs.getFirst().getId(),docs.getFirst());
            if (calls[0]++ == 0) throw new IllegalStateException("simulated partial write");
            for (Document d : docs) simulatedPoints.put(d.getId(),d);
            return null;
        }).when(store).add(anyList());
        List<Document> docs = List.of(
            Document.builder().id(EvidenceChunkPersistenceService.pointId(1,2)).text("a").build(),
            Document.builder().id(EvidenceChunkPersistenceService.pointId(1,3)).text("b").build());
        assertThrows(IllegalStateException.class, () -> store.add(docs));
        assertEquals(1,simulatedPoints.size());
        store.add(docs);
        assertEquals(2,simulatedPoints.size());
        verify(store,times(2)).add(docs); verifyNoMoreInteractions(store);
    }
    @Test void externalWritesRejectActiveDatabaseTransaction() {
        var store = mock(VectorStore.class);
        var service = new FrozenEvidenceIndexer(mock(EvidenceSnapshotStateService.class),mock(org.springframework.ai.embedding.EmbeddingModel.class),store,"isolated","isolated");
        org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(IllegalStateException.class, () -> service.index(new ImportTaskStateService.Lease(1,1,"owner",1,com.yu.transferrag.entity.ImportTaskStage.INDEXING))); }
        finally { org.springframework.transaction.support.TransactionSynchronizationManager.setActualTransactionActive(false); }
        verifyNoInteractions(store);
    }
}
