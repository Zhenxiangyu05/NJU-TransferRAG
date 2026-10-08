package com.yu.transferrag.service;

import com.yu.transferrag.entity.*;
import com.yu.transferrag.repository.ImportTaskRepository;
import org.junit.jupiter.api.*;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class ImportTaskStateServiceTest {
    ImportTaskRepository repo;
    ImportTaskStateService state;
    ImportTask task;
    LocalDateTime now = LocalDateTime.of(2026, 1, 1, 0, 0);
    @BeforeEach void setup() {
        repo = mock(ImportTaskRepository.class); state = new ImportTaskStateService(repo, 90, 3, new ImportTaskFeatureGuard(true));
        task = new ImportTask(); task.setId(1L);
        Document d = new Document(); d.setId(2L); d.setTitle("Test");
        task.setDocument(d); task.setStatus(ImportTaskStatus.QUEUED); task.setStage(ImportTaskStage.PARSING);
        task.setNextAttemptAt(now); task.setMaxAttempts(3); task.setRetryable(true);
        lenient().when(repo.lockById(1L)).thenReturn(Optional.of(task));
        lenient().when(repo.databaseNow()).thenReturn(now);
    }
    @Test void onlyOneClaimSucceeds() {
        var lease = state.claim(1L, "first").orElseThrow();
        assertEquals(1, task.getAttemptCount()); assertEquals(1, lease.generation());
        assertTrue(state.claim(1L, "second").isEmpty());
    }
    @Test void staleOwnerCannotChangeState() {
        var lease = state.claim(1L, "first").orElseThrow();
        task.setLeaseGeneration(2);
        assertFalse(state.heartbeat(lease));
        assertThrows(ImportTaskStateService.LostLease.class, () -> state.succeed(lease, 1, 1));
        assertThrows(ImportTaskStateService.LostLease.class, () -> state.fail(lease, "INDEX_FAILED", true));
    }
    @Test void expiredLeaseCannotRenewOrPersist() {
        var lease = state.claim(1L, "first").orElseThrow(); task.setLeaseUntil(now.minusSeconds(1));
        assertFalse(state.heartbeat(lease));
        assertThrows(ImportTaskStateService.LostLease.class, () -> state.requireOwned(lease));
    }
    @Test void recoverySkipsActuallyRunningLocalTask() {
        state.claim(1L, "first"); task.setLeaseUntil(now.minusSeconds(1));
        when(repo.expiredIds(any(), any(), any())).thenReturn(List.of(1L));
        assertEquals(0, state.recoverExpired(Set.of(1L)));
        assertEquals(ImportTaskStatus.PROCESSING, task.getStatus());
        assertEquals(1, state.recoverExpired(Set.of()));
        assertEquals(ImportTaskStatus.QUEUED, task.getStatus()); assertNull(task.getOwnerId());
        assertEquals(2, task.getLeaseGeneration());
    }
    @Test void indexingCheckpointSurvivesFailureAndRetry() {
        task.setStage(ImportTaskStage.INDEXING);
        var lease = state.claim(1L, "first").orElseThrow();
        state.fail(lease, "INDEX_FAILED", true); state.retry(1L);
        assertEquals(ImportTaskStage.INDEXING, task.getStage());
        assertEquals(ImportTaskStatus.QUEUED, task.getStatus());
        assertEquals(2, state.claim(1L, "second").orElseThrow().generation());
    }
    @Test void retriesAreFinite() {
        task.setStatus(ImportTaskStatus.FAILED); task.setAttemptCount(3);
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> state.retry(1L));
    }
    @Test void duplicateViewDoesNotClaimSuccess() {
        when(repo.findById(1L)).thenReturn(Optional.of(task));
        var result = state.view(1L, true);
        assertEquals("QUEUED", result.getStatus()); assertTrue(result.isDuplicate());
        assertNull(result.getIndexedChunks()); assertEquals(1L, result.getTaskId());
    }
    @Test void unprocessedTaskCannotBeMarkedSucceeded() {
        var lease = state.claim(1L,"first").orElseThrow();
        assertThrows(IllegalStateException.class, () -> state.succeed(lease,1,1));
        assertEquals(ImportTaskStatus.PROCESSING,task.getStatus());
    }
    @Test void indexingCheckpointCannotMoveBackwards() {
        task.setStage(ImportTaskStage.INDEXING);
        var lease = state.claim(1L,"first").orElseThrow();
        assertThrows(IllegalStateException.class, () -> state.stage(lease,ImportTaskStage.CHUNKING));
    }
}
