-- Manual, additive MySQL 8 migration. Run only after separate deployment approval.
-- No backfill, no document/chunk changes, no vector operations.
CREATE TABLE IF NOT EXISTS import_task (
 id BIGINT NOT NULL AUTO_INCREMENT,
 document_id BIGINT NOT NULL,
 status VARCHAR(32) NOT NULL,
 stage VARCHAR(32) NOT NULL,
 attempt_count INT NOT NULL DEFAULT 0,
 max_attempts INT NOT NULL,
 owner_id VARCHAR(128) NULL,
 lease_generation BIGINT NOT NULL DEFAULT 0,
 heartbeat_at DATETIME(6) NULL,
 lease_until DATETIME(6) NULL,
 next_attempt_at DATETIME(6) NOT NULL,
 last_error_code VARCHAR(128) NULL,
 error_summary VARCHAR(512) NULL,
 retryable BOOLEAN NOT NULL DEFAULT TRUE,
 chunk_count INT NULL,
 indexed_chunks INT NULL,
 created_at DATETIME(6) NOT NULL,
 updated_at DATETIME(6) NOT NULL,
 started_at DATETIME(6) NULL,
 finished_at DATETIME(6) NULL,
 PRIMARY KEY (id),
 UNIQUE KEY uk_import_task_document (document_id),
 INDEX idx_import_task_ready (status,next_attempt_at,id),
 INDEX idx_import_task_lease (status,lease_until,id),
 CONSTRAINT fk_import_task_document FOREIGN KEY (document_id) REFERENCES document(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
