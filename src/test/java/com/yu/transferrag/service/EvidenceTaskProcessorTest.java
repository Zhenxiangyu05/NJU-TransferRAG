package com.yu.transferrag.service;

import com.yu.transferrag.entity.*;
import org.junit.jupiter.api.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvidenceTaskProcessorTest {
    ImportTaskStateService state = mock(ImportTaskStateService.class);
    ChunkService chunks = mock(ChunkService.class);
    EvidenceChunkPersistenceService persistence = mock(EvidenceChunkPersistenceService.class);
    FrozenEvidenceIndexer index = mock(FrozenEvidenceIndexer.class);
    EvidenceLegacyVerifier legacy = mock(EvidenceLegacyVerifier.class);
    EvidenceTaskProcessor processor = new EvidenceTaskProcessor(state,chunks,persistence,index,legacy);
    ImportTaskStateService.Lease lease(ImportTaskStage stage) { return new ImportTaskStateService.Lease(1,2,"owner",1,stage); }
    EvidenceChunkPersistenceService.Snapshot snapshot() {
        return new EvidenceChunkPersistenceService.Snapshot(2,null,null,List.of(
            org.springframework.ai.document.Document.builder().id(EvidenceChunkPersistenceService.pointId(2,3)).text("test").build()));
    }
    @Test void parsingFailureDoesNotCreateCheckpointOrIndex() {
        when(chunks.prepareEvidenceChunks(2L)).thenThrow(new IllegalStateException("private input"));
        var error = assertThrows(EvidenceImportProcessor.StageFailure.class, () -> processor.process(lease(ImportTaskStage.PARSING)));
        assertEquals("PARSING_FAILED",error.code()); assertNull(error.getMessage());
        verifyNoInteractions(persistence,index);
    }
    @Test void chunkPersistenceFailureDoesNotIndex() {
        when(chunks.prepareEvidenceChunks(2L)).thenReturn(List.of(new Chunk()));
        doThrow(new IllegalStateException()).when(persistence).persistFirst(any(),any());
        var error = assertThrows(EvidenceImportProcessor.StageFailure.class, () -> processor.process(lease(ImportTaskStage.PARSING)));
        assertEquals("CHUNK_PERSISTENCE_FAILED",error.code()); verifyNoInteractions(index);
    }
    @Test void successfulImportUsesCheckpointThenNonDeletingUpsert() {
        when(chunks.prepareEvidenceChunks(2L)).thenReturn(List.of(new Chunk()));
        when(persistence.snapshot(any())).thenReturn(snapshot());
        when(index.index(any())).thenReturn(1);
        assertEquals(1,processor.process(lease(ImportTaskStage.PARSING)).indexed());
        var order = inOrder(state,persistence,index);
        order.verify(state).stage(any(),eq(ImportTaskStage.CHUNKING));
        order.verify(persistence).persistFirst(any(),any());
        order.verify(index).index(any());
    }
    @Test void indexFailureRetryReusesCommittedChunksAndIds() {
        when(persistence.snapshot(any())).thenReturn(snapshot());
        when(index.index(any())).thenThrow(new IllegalStateException("partial"))
            .thenReturn(1);
        var error = assertThrows(EvidenceImportProcessor.StageFailure.class, () -> processor.process(lease(ImportTaskStage.INDEXING)));
        assertEquals("EMBEDDING_OR_INDEX_FAILED",error.code());
        assertEquals(1,processor.process(lease(ImportTaskStage.INDEXING)).indexed());
        verifyNoInteractions(chunks); verify(persistence,never()).persistFirst(any(),any());
    }
    @Test void legacyPathOnlyVerifiesAndNeverMutatesChunksOrPoints() {
        when(persistence.snapshot(any())).thenReturn(snapshot());
        assertEquals(1,processor.process(lease(ImportTaskStage.VERIFY_EXISTING)).indexed());
        verify(legacy).verify(any()); verifyNoInteractions(chunks,index);
    }
    @Test void lostOwnerCannotPersistOrIndex() {
        when(index.index(any())).thenThrow(new ImportTaskStateService.LostLease());
        assertThrows(ImportTaskStateService.LostLease.class, () -> processor.process(lease(ImportTaskStage.INDEXING)));
        verify(index).index(any());
    }
}
