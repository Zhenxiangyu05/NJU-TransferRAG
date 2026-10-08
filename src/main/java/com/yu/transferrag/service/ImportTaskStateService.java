package com.yu.transferrag.service;

import com.yu.transferrag.dto.DocumentImportResponse;
import com.yu.transferrag.entity.*;
import com.yu.transferrag.repository.ImportTaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import java.time.LocalDateTime;
import java.util.*;

@Service
public class ImportTaskStateService {
    private final ImportTaskRepository tasks;
    private final int leaseSeconds;
    private final int maxAttempts;
    private final ImportTaskFeatureGuard feature;

    public ImportTaskStateService(ImportTaskRepository tasks,
            @Value("${app.import-tasks.lease-seconds:90}") int leaseSeconds,
            @Value("${app.import-tasks.max-attempts:3}") int maxAttempts,
            ImportTaskFeatureGuard feature) {
        if (leaseSeconds < 15 || maxAttempts < 1) throw new IllegalArgumentException("Invalid import task limits");
        this.tasks = tasks; this.leaseSeconds = leaseSeconds; this.maxAttempts = maxAttempts;
        this.feature = feature;
    }

    /** Called by the registration transaction; never commits independently. */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public ImportTask create(Document document, boolean legacy) {
        feature.requireEnabled();
        if (document.getDocumentRole() != DocumentRole.EVIDENCE) throw new IllegalArgumentException("Only EVIDENCE may have import tasks");
        ImportTask t = new ImportTask();
        t.setTaskKind(legacy ? ImportTaskKind.LEGACY_VERIFICATION : ImportTaskKind.NEW_EVIDENCE);
        LocalDateTime now = tasks.databaseNow();
        t.setDocument(document); t.setStatus(ImportTaskStatus.QUEUED);
        t.setStage(legacy ? ImportTaskStage.VERIFY_EXISTING : ImportTaskStage.PARSING);
        t.setMaxAttempts(maxAttempts); t.setRetryable(true); t.setNextAttemptAt(now);
        t.setCreatedAt(now); t.setUpdatedAt(now);
        return tasks.saveAndFlush(t);
    }

    @Transactional
    public Optional<Lease> claim(long id, String owner) {
        feature.requireEnabled();
        ImportTask t = tasks.lockById(id).orElseThrow(ImportTaskStateService::missing);
        LocalDateTime now = tasks.databaseNow();
        if (t.getStatus() != ImportTaskStatus.QUEUED || t.getNextAttemptAt().isAfter(now)) return Optional.empty();
        if (t.getAttemptCount() >= t.getMaxAttempts()) { exhausted(t, now); return Optional.empty(); }
        t.setStatus(ImportTaskStatus.PROCESSING); t.setOwnerId(owner);
        t.setLeaseGeneration(t.getLeaseGeneration() + 1); t.setAttemptCount(t.getAttemptCount() + 1);
        t.setStartedAt(now); t.setFinishedAt(null); t.setUpdatedAt(now);
        t.setHeartbeatAt(now); t.setLeaseUntil(now.plusSeconds(leaseSeconds));
        t.setLastErrorCode(null); t.setErrorSummary(null);
        return Optional.of(new Lease(t.getId(), t.getDocument().getId(), owner, t.getLeaseGeneration(), t.getStage()));
    }

    @Transactional
    public boolean heartbeat(Lease lease) {
        feature.requireEnabled();
        ImportTask t = tasks.lockById(lease.taskId()).orElseThrow(ImportTaskStateService::missing);
        LocalDateTime now = tasks.databaseNow();
        if (!owns(t, lease, now)) return false;
        t.setHeartbeatAt(now); t.setLeaseUntil(now.plusSeconds(leaseSeconds)); t.setUpdatedAt(now);
        return true;
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.MANDATORY)
    public ImportTask requireOwned(Lease lease) {
        feature.requireEnabled();
        ImportTask t = tasks.lockById(lease.taskId()).orElseThrow(ImportTaskStateService::missing);
        if (!owns(t, lease, tasks.databaseNow())) throw new LostLease();
        return t;
    }

    @Transactional
    public void stage(Lease lease, ImportTaskStage stage) {
        ImportTask t = requireOwned(lease);
        if (stage != ImportTaskStage.CHUNKING
                || (t.getStage() != ImportTaskStage.PARSING && t.getStage() != ImportTaskStage.CHUNKING))
            throw new IllegalStateException("Invalid import stage transition");
        t.setStage(stage); t.setUpdatedAt(tasks.databaseNow());
    }

    @Transactional
    public void succeed(Lease lease, int chunks, int indexed) {
        ImportTask t = requireOwned(lease);
        if (t.getStage() != ImportTaskStage.INDEXING && t.getStage() != ImportTaskStage.VERIFY_EXISTING)
            throw new IllegalStateException("No completed processing checkpoint");
        if (chunks <= 0 || chunks != indexed) throw new IllegalStateException("Incomplete index");
        if (t.getStage() == ImportTaskStage.INDEXING && !Objects.equals(t.getChunkCount(), chunks))
            throw new IllegalStateException("Index differs from committed checkpoint");
        LocalDateTime now = tasks.databaseNow();
        t.setChunkCount(chunks); t.setIndexedChunks(indexed); t.setStage(ImportTaskStage.COMPLETED);
        t.setStatus(ImportTaskStatus.SUCCEEDED); t.setFinishedAt(now); t.setUpdatedAt(now);
        t.setOwnerId(null); t.setLeaseUntil(null);
    }

    @Transactional
    public void fail(Lease lease, String code, boolean retryable) {
        ImportTask t = requireOwned(lease); LocalDateTime now = tasks.databaseNow();
        t.setStatus(ImportTaskStatus.FAILED); t.setRetryable(retryable);
        // Never persist exception messages, source text, provider bodies or credentials.
        t.setLastErrorCode(code); t.setErrorSummary("Import failed at " + t.getStage().name());
        t.setFinishedAt(now); t.setUpdatedAt(now); t.setOwnerId(null); t.setLeaseUntil(null);
    }

    @Transactional
    public void retry(long id) {
        feature.requireEnabled();
        ImportTask t = tasks.lockById(id).orElseThrow(ImportTaskStateService::missing);
        if (t.getStatus() == ImportTaskStatus.QUEUED || t.getStatus() == ImportTaskStatus.PROCESSING || t.getStatus() == ImportTaskStatus.SUCCEEDED) return;
        if (!t.isRetryable() || t.getAttemptCount() >= t.getMaxAttempts())
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Task requires administrator review");
        requeue(t, tasks.databaseNow());
    }

    @Transactional
    public int recoverExpired(Set<Long> locallyRunning) {
        feature.requireEnabled();
        LocalDateTime now = tasks.databaseNow(); int recovered = 0;
        for (Long id : tasks.expiredIds(ImportTaskStatus.PROCESSING, now, PageRequest.of(0, 32))) {
            if (locallyRunning.contains(id)) continue; // Expiry alone never starts a second local worker.
            ImportTask t = tasks.lockById(id).orElseThrow(ImportTaskStateService::missing);
            if (t.getStatus() != ImportTaskStatus.PROCESSING || t.getLeaseUntil() == null || t.getLeaseUntil().isAfter(now)) continue;
            t.setLeaseGeneration(t.getLeaseGeneration() + 1);
            if (t.getAttemptCount() >= t.getMaxAttempts()) exhausted(t, now); else requeue(t, now);
            recovered++;
        }
        return recovered;
    }

    @Transactional(readOnly = true)
    public List<Long> readyIds(int limit) {
        return tasks.readyIds(ImportTaskStatus.QUEUED, tasks.databaseNow(), PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public DocumentImportResponse view(long id, boolean duplicate) {
        ImportTask t = tasks.findById(id).orElseThrow(ImportTaskStateService::missing);
        Document d = t.getDocument(); DocumentImportResponse r = new DocumentImportResponse();
        r.setTaskId(t.getId()); r.setDocumentId(d.getId()); r.setTitle(d.getTitle());
        r.setDepartment(d.getDepartment()); r.setYear(d.getYear()); r.setSourceType(d.getSourceType());
        r.setStatus(t.getStatus().name()); r.setStage(t.getStage().name()); r.setDuplicate(duplicate);
        r.setChunkCount(t.getChunkCount()); r.setIndexedChunks(t.getIndexedChunks());
        r.setAttemptCount(t.getAttemptCount()); r.setRetryable(t.getStatus() == ImportTaskStatus.FAILED
                && t.isRetryable() && t.getAttemptCount() < t.getMaxAttempts());
        r.setError(t.getErrorSummary()); return r;
    }

    private static boolean owns(ImportTask t, Lease l, LocalDateTime now) {
        return t.getStatus() == ImportTaskStatus.PROCESSING && Objects.equals(t.getOwnerId(), l.owner())
                && Objects.equals(t.getDocument().getId(), l.documentId())
                && t.getLeaseGeneration() == l.generation() && t.getLeaseUntil() != null && t.getLeaseUntil().isAfter(now);
    }
    private void requeue(ImportTask t, LocalDateTime now) {
        t.setStatus(ImportTaskStatus.QUEUED); t.setOwnerId(null); t.setLeaseUntil(null);
        t.setNextAttemptAt(now); t.setUpdatedAt(now);
    }
    private void exhausted(ImportTask t, LocalDateTime now) {
        t.setStatus(ImportTaskStatus.FAILED); t.setRetryable(false); t.setLastErrorCode("ATTEMPTS_EXHAUSTED");
        t.setErrorSummary("Import attempt budget exhausted"); t.setUpdatedAt(now); t.setFinishedAt(now);
        t.setOwnerId(null); t.setLeaseUntil(null);
    }
    private static ResponseStatusException missing() { return new ResponseStatusException(HttpStatus.NOT_FOUND, "Import task not found"); }
    public record Lease(long taskId, long documentId, String owner, long generation, ImportTaskStage stage) {}
    public static class LostLease extends RuntimeException {}
}
