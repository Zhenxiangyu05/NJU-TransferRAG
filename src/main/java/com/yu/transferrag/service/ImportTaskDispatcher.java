package com.yu.transferrag.service;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.*;
import java.util.Set;
import java.util.concurrent.*;

@Component
@ConditionalOnProperty(name = "app.import-tasks.enabled", havingValue = "true")
public class ImportTaskDispatcher {
    private static final Logger LOG = LoggerFactory.getLogger(ImportTaskDispatcher.class);
    private final ImportTaskStateService state;
    private final EvidenceImportWorker worker;
    private final ThreadPoolExecutor executor;
    private final Set<Long> submitted = ConcurrentHashMap.newKeySet();
    public ImportTaskDispatcher(ImportTaskStateService state, EvidenceImportWorker worker, ThreadPoolExecutor evidenceImportExecutor) {
        this.state = state; this.worker = worker; this.executor = evidenceImportExecutor;
    }
    @Scheduled(fixedDelayString = "${app.import-tasks.scan-ms:1000}")
    public void scan() {
        try {
            state.recoverExpired(worker.runningIds());
            for (Long id : state.readyIds(32)) {
                if (!submitted.add(id)) continue;
                try {
                    executor.execute(() -> {
                        try { worker.run(id); } finally { submitted.remove(id); }
                    });
                } catch (RejectedExecutionException full) {
                    submitted.remove(id); break; // Database remains QUEUED; scan it again later.
                }
            }
        } catch (RuntimeException error) {
            LOG.warn("Import dispatch unavailable: exception={}", error.getClass().getSimpleName());
        }
    }
    @Scheduled(fixedDelayString = "${app.import-tasks.heartbeat-ms:10000}")
    public void heartbeat() { worker.heartbeat(); }
}
