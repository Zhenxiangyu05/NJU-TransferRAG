package com.yu.transferrag.service;

import com.yu.transferrag.entity.*;
import com.yu.transferrag.repository.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvidenceChunkPersistenceServiceTest {
    ImportTaskStateService state = mock(ImportTaskStateService.class);
    ChunkRepository chunks = mock(ChunkRepository.class);
    ImportTaskRepository tasks = mock(ImportTaskRepository.class);
    VectorIndexService index = new VectorIndexService(chunks,mock(org.springframework.ai.vectorstore.VectorStore.class));
    EvidenceIndexSnapshotRepository snapshots = mock(EvidenceIndexSnapshotRepository.class);
    EvidenceChunkPersistenceService service = new EvidenceChunkPersistenceService(state,chunks,tasks,index,snapshots);
    ImportTaskStateService.Lease lease = new ImportTaskStateService.Lease(1,2,"owner",1,ImportTaskStage.CHUNKING);
    ImportTask task() {
        var d = new Document(); d.setId(2L);
        var t = new ImportTask(); t.setDocument(d); t.setTaskKind(ImportTaskKind.NEW_EVIDENCE); t.setStage(ImportTaskStage.CHUNKING);
        when(state.requireOwned(lease)).thenReturn(t); return t;
    }
    Chunk row() { var c = new Chunk(); c.setContent("test"); c.setChunkIndex(0); return c; }
    @Test void firstChunksAndCheckpointArePreparedInSameTransactionalMethod() {
        var t = task(); var c = row();
        doAnswer(i -> { c.setId(3L); return i.getArgument(0); }).when(chunks).saveAllAndFlush(anyList());
        when(chunks.findByDocument_IdOrderByChunkIndexAsc(2L)).thenReturn(List.of());
        service.persistFirst(lease,List.of(c));
        verify(chunks).saveAllAndFlush(List.of(c)); assertSame(t.getDocument(),c.getDocument());
        assertEquals(ImportTaskStage.INDEXING,t.getStage()); assertEquals(1,t.getChunkCount());
    }
    @Test void failedPersistenceDoesNotAdvanceCheckpoint() {
        var t = task();
        when(chunks.findByDocument_IdOrderByChunkIndexAsc(2L)).thenReturn(List.of());
        doThrow(new IllegalStateException()).when(chunks).saveAllAndFlush(anyList());
        assertThrows(IllegalStateException.class, () -> service.persistFirst(lease,List.of(row())));
        assertEquals(ImportTaskStage.CHUNKING,t.getStage()); assertNull(t.getChunkCount());
        // Actual row rollback is asserted only by the isolated MySQL test.
    }
    @Test void preexistingChunksAreNeverDeletedOrReplaced() {
        task(); when(chunks.findByDocument_IdOrderByChunkIndexAsc(2L)).thenReturn(List.of(row()));
        assertThrows(EvidenceImportProcessor.StageFailure.class, () -> service.persistFirst(lease,List.of(row())));
        verify(chunks,never()).deleteByDocument_Id(anyLong()); verify(chunks,never()).saveAllAndFlush(anyList());
    }
    @Test void staleOwnerNeverReachesChunkRepository() {
        when(state.requireOwned(lease)).thenThrow(new ImportTaskStateService.LostLease());
        assertThrows(ImportTaskStateService.LostLease.class, () -> service.persistFirst(lease,List.of(row())));
        verifyNoInteractions(chunks);
    }
    @Test void snapshotHasStableIdAndUnchangedRetrievalMetadata() {
        var t = task(); t.setStage(ImportTaskStage.INDEXING); t.setChunkCount(1);
        var c = row(); c.setDocument(t.getDocument()); c.setId(3L);
        when(chunks.findByDocument_IdOrderByChunkIndexAsc(2L)).thenReturn(List.of(c));
        var vector = service.snapshot(lease).vectors().getFirst();
        assertEquals(EvidenceChunkPersistenceService.pointId(2,3),vector.getId());
        assertEquals("EVIDENCE",vector.getMetadata().get("documentRole"));
        assertEquals("2",vector.getMetadata().get("documentId")); assertEquals("3",vector.getMetadata().get("chunkId"));
    }
    @Test void checkpointCountMismatchRequiresReview() {
        var t = task(); t.setStage(ImportTaskStage.INDEXING); t.setChunkCount(2);
        when(chunks.findByDocument_IdOrderByChunkIndexAsc(2L)).thenReturn(List.of(row()));
        assertThrows(EvidenceImportProcessor.StageFailure.class, () -> service.snapshot(lease));
    }

    @Test void retrySnapshotKeepsExactContentMetadataAndStablePointId() {
        var t = task(); t.setStage(ImportTaskStage.INDEXING); t.setChunkCount(1);
        var c = row(); c.setDocument(t.getDocument()); c.setId(3L);
        c.setDepartment("isolated department"); c.setPolicyYear(2026);
        when(chunks.findByDocument_IdOrderByChunkIndexAsc(2L)).thenReturn(List.of(c));
        var first = service.snapshot(lease).vectors().getFirst();
        t.setLeaseGeneration(2); t.setOwnerId("new worker");
        var next = new ImportTaskStateService.Lease(1,2,"new worker",2,ImportTaskStage.INDEXING);
        when(state.requireOwned(next)).thenReturn(t);
        var second = service.snapshot(next).vectors().getFirst();
        assertEquals(first.getId(),second.getId()); assertEquals(first.getText(),second.getText());
        assertEquals(first.getMetadata(),second.getMetadata());
        verify(chunks,never()).saveAllAndFlush(anyList()); verify(chunks,never()).deleteByDocument_Id(anyLong());
    }
}
