-- Manual MySQL 8 migration for V2. This file is intentionally not auto-executed.
-- It is idempotent and does not delete or rebuild existing documents, chunks, or Qdrant data.

SET @schema_name := DATABASE();

SELECT COUNT(*) INTO @has_document_role
FROM information_schema.columns
WHERE table_schema = @schema_name AND table_name = 'document' AND column_name = 'document_role';
SET @statement := IF(
        @has_document_role = 0,
        'ALTER TABLE `document` ADD COLUMN `document_role` VARCHAR(16) NOT NULL DEFAULT ''EVIDENCE''',
        'SELECT 1'
);
PREPARE migration_statement FROM @statement;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

SELECT COUNT(*) INTO @has_section
FROM information_schema.columns
WHERE table_schema = @schema_name AND table_name = 'chunk' AND column_name = 'section';
SET @statement := IF(
        @has_section = 0,
        'ALTER TABLE `chunk` ADD COLUMN `section` VARCHAR(255) NULL',
        'SELECT 1'
);
PREPARE migration_statement FROM @statement;
EXECUTE migration_statement;
DEALLOCATE PREPARE migration_statement;

-- Defensive backfill for installations where a prior manual schema change left null roles.
UPDATE `document`
SET `document_role` = 'EVIDENCE'
WHERE `document_role` IS NULL OR TRIM(`document_role`) = '';

CREATE TABLE IF NOT EXISTS `evidence_ref` (
    `id` BIGINT NOT NULL AUTO_INCREMENT,
    `canonical_chunk_id` BIGINT NOT NULL,
    `evidence_document_id` BIGINT NOT NULL,
    `fact_index` INT NOT NULL,
    `source_page` INT NULL,
    `evidence_text` TEXT NOT NULL,
    PRIMARY KEY (`id`),
    INDEX `idx_evidence_ref_canonical_chunk` (`canonical_chunk_id`),
    INDEX `idx_evidence_ref_evidence_document` (`evidence_document_id`),
    UNIQUE KEY `uk_evidence_ref_fact_source_page`
        (`canonical_chunk_id`, `fact_index`, `evidence_document_id`, `source_page`),
    CONSTRAINT `fk_evidence_ref_canonical_chunk`
        FOREIGN KEY (`canonical_chunk_id`) REFERENCES `chunk` (`id`) ON DELETE CASCADE,
    CONSTRAINT `fk_evidence_ref_evidence_document`
        FOREIGN KEY (`evidence_document_id`) REFERENCES `document` (`id`) ON DELETE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
