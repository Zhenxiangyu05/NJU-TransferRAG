package com.yu.transferrag.service;

import com.yu.transferrag.exception.ImportTasksDisabledException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/** Fixed at process startup; disabling processing requires a restart, not an in-flight toggle. */
@Service
public class ImportTaskFeatureGuard {
    private final boolean enabled;

    public ImportTaskFeatureGuard(@Value("${app.import-tasks.enabled:false}") boolean enabled) {
        this.enabled = enabled;
    }

    public void requireEnabled() {
        if (!enabled) throw new ImportTasksDisabledException();
    }
}
