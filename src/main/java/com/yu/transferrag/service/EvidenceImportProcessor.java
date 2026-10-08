package com.yu.transferrag.service;

/** Implementations execute parser/network work outside any enclosing transaction. */
public interface EvidenceImportProcessor {
    Result process(ImportTaskStateService.Lease lease);
    record Result(int chunks, int indexed) {}
    class StageFailure extends RuntimeException {
        private final String code;
        private final boolean retryable;
        public StageFailure(String code, boolean retryable) { this.code = code; this.retryable = retryable; }
        public String code() { return code; }
        public boolean retryable() { return retryable; }
    }
}
