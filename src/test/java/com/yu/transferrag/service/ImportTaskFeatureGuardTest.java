package com.yu.transferrag.service;

import com.yu.transferrag.entity.*;
import com.yu.transferrag.exception.ImportTasksDisabledException;
import com.yu.transferrag.repository.*;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ImportTaskFeatureGuardTest {
    private ImportTaskStateService disabled(ImportTaskRepository tasks) {
        return new ImportTaskStateService(tasks, 90, 3, new ImportTaskFeatureGuard(false));
    }

    @Test void disabledStateMutationsRejectBeforeAnyRepositoryAccess() {
        var tasks = mock(ImportTaskRepository.class);
        var state = disabled(tasks);
        var lease = new ImportTaskStateService.Lease(1, 2, "test", 1, ImportTaskStage.INDEXING);
        List<Runnable> writes = List.of(
                () -> state.create(new Document(), false),
                () -> state.create(new Document(), true),
                () -> state.retry(1),
                () -> state.claim(1, "test"),
                () -> state.heartbeat(lease),
                () -> state.requireOwned(lease),
                () -> state.stage(lease, ImportTaskStage.CHUNKING),
                () -> state.succeed(lease, 1, 1),
                () -> state.fail(lease, "TEST_ONLY", true),
                () -> state.recoverExpired(Set.of()));
        for (Runnable write : writes) {
            var error = assertThrows(ImportTasksDisabledException.class, write::run);
            assertEquals(503, error.getStatusCode().value());
        }
        verifyNoInteractions(tasks);
    }

    @Test void disabledRegistrationRejectsBeforeDocumentOrTaskAccess() {
        var documents = mock(DocumentRepository.class);
        var tasks = mock(ImportTaskRepository.class);
        var state = mock(ImportTaskStateService.class);
        var registration = new EvidenceRegistrationService(documents, tasks, state, new ImportTaskFeatureGuard(false));
        assertThrows(ImportTasksDisabledException.class, () -> registration.register(new Document()));
        assertThrows(ImportTasksDisabledException.class, () -> registration.resolveHash("test-hash"));
        verifyNoInteractions(documents, tasks, state);
    }

    @Test void disabledWorkerCannotClaimOrExecuteProcessorEvenIfCalledManually() {
        var tasks = mock(ImportTaskRepository.class);
        var processor = mock(EvidenceImportProcessor.class);
        var worker = new EvidenceImportWorker(disabled(tasks), processor, 60);
        assertThrows(ImportTasksDisabledException.class, () -> worker.run(1));
        assertTrue(worker.runningIds().isEmpty());
        verifyNoInteractions(tasks, processor);
    }

    @Test void disabledReadOnlyTaskStatusAndReadyListRemainAvailable() {
        var tasks = mock(ImportTaskRepository.class);
        var task = new ImportTask();
        var document = new Document(); document.setId(2L); document.setTitle("test fixture");
        task.setId(1L); task.setDocument(document); task.setStatus(ImportTaskStatus.FAILED);
        task.setStage(ImportTaskStage.INDEXING); task.setMaxAttempts(3); task.setAttemptCount(1);
        when(tasks.findById(1L)).thenReturn(Optional.of(task));
        when(tasks.databaseNow()).thenReturn(LocalDateTime.now());
        when(tasks.readyIds(any(), any(), any())).thenReturn(List.of(3L));
        var state = disabled(tasks);
        assertEquals("FAILED", state.view(1, false).getStatus());
        assertEquals(List.of(3L), state.readyIds(1));
        assertEquals(ImportTaskStatus.FAILED, task.getStatus());
        verify(tasks, never()).lockById(anyLong());
        verify(tasks, never()).saveAndFlush(any());
    }

    @Test void disabledFlagDoesNotChangeReadinessIncludingLegacyOrUnknown() {
        var documents = mock(DocumentRepository.class);
        var legacy = mock(DocumentReadinessView.class);
        when(legacy.getDocumentId()).thenReturn(1L); when(legacy.getDocumentRole()).thenReturn(DocumentRole.EVIDENCE);
        when(legacy.getTaskId()).thenReturn(null);
        var unknown = mock(DocumentReadinessView.class);
        when(unknown.getDocumentId()).thenReturn(2L); when(unknown.getDocumentRole()).thenReturn(DocumentRole.EVIDENCE);
        when(unknown.getTaskId()).thenReturn(2L); when(unknown.getTaskKind()).thenReturn(ImportTaskKind.UNKNOWN);
        when(unknown.getStatus()).thenReturn(ImportTaskStatus.SUCCEEDED);
        when(documents.findReadinessByIds(any())).thenReturn(List.of(legacy, unknown));
        assertEquals(Set.of(1L), new EvidenceReadinessService(documents).readyDocumentIds(List.of(1L, 2L)));
    }

    @Test void enabledGuardAllowsExistingClaimAndRetryStateMachine() {
        var tasks = mock(ImportTaskRepository.class);
        var task = new ImportTask(); var document = new Document(); document.setId(2L);
        var now = LocalDateTime.now(); task.setId(1L); task.setDocument(document);
        task.setStatus(ImportTaskStatus.FAILED); task.setStage(ImportTaskStage.INDEXING);
        task.setRetryable(true); task.setMaxAttempts(3); task.setAttemptCount(1);
        when(tasks.lockById(1L)).thenReturn(Optional.of(task)); when(tasks.databaseNow()).thenReturn(now);
        var state = new ImportTaskStateService(tasks, 90, 3, new ImportTaskFeatureGuard(true));
        state.retry(1); assertEquals(ImportTaskStatus.QUEUED, task.getStatus());
        assertTrue(state.claim(1, "next").isPresent()); assertEquals(ImportTaskStatus.PROCESSING, task.getStatus());
    }
}
