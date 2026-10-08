package com.yu.transferrag.service;

import com.yu.transferrag.dto.DocumentImportResponse;
import com.yu.transferrag.entity.Document;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DocumentImportServiceTest {
    @Mock DocumentService documents;
    @Mock EvidenceRegistrationService registration;
    @Mock ImportTaskStateService state;
    DocumentImportService service;
    MockMultipartFile file = new MockMultipartFile("file", "first.md", "text/markdown", new byte[]{1});
    Document candidate;
    @BeforeEach void setup() {
        service = new DocumentImportService(documents, registration, state, new ImportTaskFeatureGuard(true));
        candidate = new Document(); candidate.setFileHash("hash");
    }
    void prepare(boolean duplicate, String status, boolean retryable) {
        when(documents.prepareEvidenceUpload(file, "first", "软件学院", 2026, "PERSONAL", "DEPARTMENT")).thenReturn(candidate);
        when(registration.register(candidate)).thenReturn(new EvidenceRegistrationService.Registration(9, 1, duplicate, null));
        when(state.view(9, duplicate)).thenReturn(response(status, duplicate, retryable));
    }
    DocumentImportResponse response(String status, boolean duplicate, boolean retryable) {
        var r = new DocumentImportResponse(); r.setDocumentId(1L); r.setTaskId(9L);
        r.setStatus(status); r.setDuplicate(duplicate); r.setRetryable(retryable); return r;
    }
    DocumentImportResponse submit() { return service.importDocument(file, null, "软件学院", 2026, "PERSONAL"); }
    @Test void queuedRegistrationDoesNotParseOrIndexInRequest() {
        prepare(false, "QUEUED", true);
        assertEquals("QUEUED", submit().getStatus());
        verify(registration).register(candidate);
    }
    @Test void duplicateProcessingIsNotSuccess() {
        prepare(true, "PROCESSING", true);
        assertEquals("PROCESSING", submit().getStatus()); verify(state, never()).retry(anyLong());
    }
    @Test void duplicateSucceededKeepsActualStatus() {
        prepare(true, "SUCCEEDED", false);
        assertEquals("SUCCEEDED", submit().getStatus()); verify(state, never()).retry(anyLong());
    }
    @Test void failedDuplicateRequestsRetry() {
        prepare(true, "FAILED", true);
        when(state.view(9, true)).thenReturn(response("FAILED",true,true), response("QUEUED",true,true));
        assertEquals("QUEUED", submit().getStatus()); verify(state).retry(9);
    }
    @Test void exhaustedDuplicateRemainsFailed() {
        prepare(true, "FAILED", false);
        assertEquals("FAILED", submit().getStatus()); verify(state, never()).retry(anyLong());
    }
    @Test void uniqueHashRaceIsResolvedOutsideFailedRegistrationTransaction() {
        when(documents.prepareEvidenceUpload(file, "first", "软件学院", 2026, "PERSONAL", "DEPARTMENT")).thenReturn(candidate);
        when(registration.register(candidate)).thenThrow(new DataIntegrityViolationException("unique"));
        when(registration.resolveHash("hash")).thenReturn(new EvidenceRegistrationService.Registration(9,1,true,null));
        when(state.view(9,true)).thenReturn(response("QUEUED",true,true));
        assertTrue(submit().isDuplicate()); verify(registration).resolveHash("hash");
    }
    @Test void batchPendingIsNotCountedAsSuccess() {
        prepare(false, "QUEUED", true);
        var result = service.importBatch(List.of(file), "软件学院", 2026, "PERSONAL");
        assertEquals(1, result.getPending()); assertEquals(0, result.getSuccess());
        assertEquals(9L, result.getResults().getFirst().getTaskId());
    }
    @Test void disabledImportNeverSavesFile() {
        service = new DocumentImportService(documents,registration,state,new ImportTaskFeatureGuard(false));
        assertThrows(ResponseStatusException.class, this::submit); verifyNoInteractions(documents,registration,state);
    }
}
