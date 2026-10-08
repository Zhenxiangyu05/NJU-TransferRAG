-- Third additive migration, after task schema and readiness schema. No historical backfill.
CREATE TABLE IF NOT EXISTS evidence_index_snapshot (
 task_id BIGINT NOT NULL,
 input_json LONGTEXT NOT NULL,
 input_sha256 VARCHAR(64) NOT NULL,
 points_json LONGTEXT NULL,
 points_sha256 VARCHAR(64) NULL,
 PRIMARY KEY (task_id),
 CONSTRAINT fk_evidence_snapshot_task FOREIGN KEY (task_id) REFERENCES import_task(id) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
