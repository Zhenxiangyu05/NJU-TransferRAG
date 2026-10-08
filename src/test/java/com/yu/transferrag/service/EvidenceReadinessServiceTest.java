package com.yu.transferrag.service;

import com.yu.transferrag.dto.SearchResultResponse;
import com.yu.transferrag.entity.*;
import com.yu.transferrag.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvidenceReadinessServiceTest {
    DocumentRepository repository = mock(DocumentRepository.class);
    EvidenceReadinessService gate = new EvidenceReadinessService(repository);
    DocumentReadinessView row(long id, DocumentRole role, ImportTaskKind kind, ImportTaskStatus status) {
        var row = mock(DocumentReadinessView.class);
        lenient().when(row.getDocumentId()).thenReturn(id);
        lenient().when(row.getDocumentRole()).thenReturn(role);
        lenient().when(row.getTaskId()).thenReturn(kind == null ? null : id);
        lenient().when(row.getTaskKind()).thenReturn(kind);
        lenient().when(row.getStatus()).thenReturn(status);
        return row;
    }
    @Test void unknownKindMissingDocumentAndCanonicalCannotGrantPublication() {
        var rows = List.of(
                row(1,DocumentRole.EVIDENCE,ImportTaskKind.UNKNOWN,ImportTaskStatus.SUCCEEDED),
                row(2,DocumentRole.CANONICAL,null,null));
        when(repository.findReadinessByIds(anyCollection())).thenReturn(rows);
        assertTrue(gate.readyDocumentIds(List.of(1L,2L,3L)).isEmpty());
    }
    @Test void databaseFailureIs503NotUnknownPublication() {
        when(repository.findReadinessByIds(anyCollection())).thenThrow(new IllegalStateException("test SQL failure"));
        var error = assertThrows(ResponseStatusException.class, () -> gate.readyDocumentIds(List.of(1L)));
        assertEquals(503,error.getStatusCode().value());
        assertFalse(error.getReason().contains("SQL"));
    }
    @Test void candidateChecksAreOneBatchNotOneQueryPerChunk() {
        var rows = List.of(
                row(1,DocumentRole.EVIDENCE,ImportTaskKind.NEW_EVIDENCE,ImportTaskStatus.PROCESSING),
                row(2,DocumentRole.EVIDENCE,null,null));
        when(repository.findReadinessByIds(Set.of(1L,2L))).thenReturn(rows);
        List<SearchResultResponse> input = new ArrayList<>();
        for (long id : new long[]{1,1,2,2,2}) {
            var c = new SearchResultResponse(); c.setDocumentId(id); c.setDocumentRole(DocumentRole.EVIDENCE); input.add(c);
        }
        assertEquals(3,gate.filterCandidates(input).size());
        verify(repository,times(1)).findReadinessByIds(Set.of(1L,2L));
        verifyNoMoreInteractions(repository);
    }
    @Test void kindCannotChangeWithMutableStageOrStatus() {
        var task = new ImportTask(); task.setTaskKind(ImportTaskKind.NEW_EVIDENCE);
        task.setStage(ImportTaskStage.VERIFY_EXISTING); task.setStatus(ImportTaskStatus.SUCCEEDED);
        assertThrows(IllegalArgumentException.class, () -> task.setTaskKind(ImportTaskKind.LEGACY_VERIFICATION));
        assertEquals(ImportTaskKind.NEW_EVIDENCE,task.getTaskKind());
    }
}
