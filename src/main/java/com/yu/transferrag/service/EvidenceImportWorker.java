package com.yu.transferrag.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class EvidenceImportWorker {
    private final ImportTaskStateService state;
    private final EvidenceImportProcessor processor;
    private final long maxRuntimeNanos;
    private final String owner = UUID.randomUUID().toString();
    private final Map<Long, Running> running = new ConcurrentHashMap<>();

    public EvidenceImportWorker(ImportTaskStateService state, EvidenceImportProcessor processor,
            @Value("${app.import-tasks.max-runtime-seconds:1800}") long maxRuntimeSeconds) {
        if (maxRuntimeSeconds < 1) throw new IllegalArgumentException("Invalid import runtime");
        this.state = state; this.processor = processor;
        maxRuntimeNanos = java.util.concurrent.TimeUnit.SECONDS.toNanos(maxRuntimeSeconds);
    }
    public void run(long id) {
        var claimed = state.claim(id, owner);
        if (claimed.isEmpty()) return;
        var lease = claimed.get();
        Running execution = new Running(lease, Thread.currentThread(), System.nanoTime());
        running.put(id, execution);
        try {
            var result = processor.process(lease);
            if (Thread.currentThread().isInterrupted()) throw new EvidenceImportProcessor.StageFailure("EXECUTION_CANCELLED", true);
            state.succeed(lease, result.chunks(), result.indexed());
        } catch (ImportTaskStateService.LostLease ignored) {
            // A stale worker must not write failure over a newer attempt.
        } catch (RuntimeException error) {
            String code = error instanceof EvidenceImportProcessor.StageFailure failure ? failure.code() : "IMPORT_FAILED";
            boolean retryable = !(error instanceof EvidenceImportProcessor.StageFailure failure) || failure.retryable();
            try { state.fail(lease, code, retryable); } catch (ImportTaskStateService.LostLease ignored) {}
        } finally {
            running.remove(id, execution);
        }
    }
    public Set<Long> runningIds() { return Set.copyOf(running.keySet()); }
    public void heartbeat() {
        for (Running execution : running.values()) {
            if (System.nanoTime() - execution.startedNanos() >= maxRuntimeNanos) {
                execution.thread().interrupt();
                continue; // Do not renew or requeue until this local execution has actually exited.
            }
            try {
                if (!state.heartbeat(execution.lease())) execution.thread().interrupt();
            } catch (RuntimeException unavailable) { execution.thread().interrupt(); }
        }
    }
    private record Running(ImportTaskStateService.Lease lease, Thread thread, long startedNanos) {}
}
