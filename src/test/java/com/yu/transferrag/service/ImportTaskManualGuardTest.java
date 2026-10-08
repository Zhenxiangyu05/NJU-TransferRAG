package com.yu.transferrag.service;
import com.yu.transferrag.entity.*;
import com.yu.transferrag.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class ImportTaskManualGuardTest {
    DocumentRepository documents = mock(DocumentRepository.class);
    ImportTaskRepository tasks = mock(ImportTaskRepository.class);
    @Test void enabledEvidenceManualWritesAreBlocked() {
        when(documents.findById(1L)).thenReturn(Optional.of(new Document()));
        assertThrows(ResponseStatusException.class, () -> new ImportTaskManualGuard(documents,tasks,true).check(1));
    }
    @Test void disabledButManagedEvidenceAlsoBlocked() {
        when(documents.findById(1L)).thenReturn(Optional.of(new Document()));
        when(tasks.findByDocument_Id(1L)).thenReturn(Optional.of(new ImportTask()));
        assertThrows(ResponseStatusException.class, () -> new ImportTaskManualGuard(documents,tasks,false).check(1));
    }
    @Test void canonicalRemainsOnOriginalPath() {
        Document d = new Document(); d.setDocumentRole(DocumentRole.CANONICAL);
        when(documents.findById(1L)).thenReturn(Optional.of(d));
        assertDoesNotThrow(() -> new ImportTaskManualGuard(documents,tasks,true).check(1));
        assertDoesNotThrow(() -> new ImportTaskManualGuard(documents,tasks,true).checkIndexMutation(1));
        verifyNoInteractions(tasks);
    }
    @Test void destructiveServicePathsRejectEvenNoTaskLegacyEvidenceWhenDisabled() {
        when(documents.findById(1L)).thenReturn(Optional.of(new Document()));
        var guard=new ImportTaskManualGuard(documents,tasks,false);
        assertThrows(ResponseStatusException.class,() -> guard.checkIndexMutation(1));
        var index=new VectorIndexService(mock(ChunkRepository.class),mock(org.springframework.ai.vectorstore.VectorStore.class));
        index.setManualGuard(guard);
        assertThrows(ResponseStatusException.class,() -> index.indexDocument(1L));
        assertThrows(ResponseStatusException.class,() -> index.removeDocumentIndex(1L));
        var chunks=new ChunkService(documents,mock(ChunkRepository.class),mock(DocumentParserService.class));chunks.setManualGuard(guard);
        assertThrows(ResponseStatusException.class,() -> chunks.createChunks(1L));
    }
}
