-- Apply AFTER evidence-import-task-schema.sql, before deploying the readiness gate.
-- Existing tasks cannot safely be classified from their mutable stage. UNKNOWN fails closed.
-- An operator must independently verify registration origin before explicitly classifying
-- any pre-existing UNKNOWN task. Never automatically infer legacy origin from stage/status.
SET @readiness_ddl = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE() AND table_name = 'import_task' AND column_name = 'task_kind') = 0,
    'ALTER TABLE import_task ADD COLUMN task_kind VARCHAR(32) NOT NULL DEFAULT ''UNKNOWN''',
    'SELECT 1');
PREPARE readiness_statement FROM @readiness_ddl;
EXECUTE readiness_statement;
DEALLOCATE PREPARE readiness_statement;
