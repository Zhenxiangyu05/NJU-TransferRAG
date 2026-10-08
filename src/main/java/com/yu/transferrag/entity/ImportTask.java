package com.yu.transferrag.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "import_task", uniqueConstraints = @UniqueConstraint(name = "uk_import_task_document", columnNames = "document_id"),
       indexes = {@Index(name = "idx_import_task_ready", columnList = "status,next_attempt_at,id"),
                  @Index(name = "idx_import_task_lease", columnList = "status,lease_until,id")})
public class ImportTask {
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32, updatable = false)
    private ImportTaskKind taskKind;
    public ImportTaskKind getTaskKind() { return taskKind; }
    public void setTaskKind(ImportTaskKind value) {
        if (value == null || (taskKind != null && taskKind != value))
            throw new IllegalArgumentException("Task kind is immutable");
        taskKind = value;
    }
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "document_id", nullable = false, foreignKey = @ForeignKey(name = "fk_import_task_document"))
    private Document document;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private ImportTaskStatus status;
    @Enumerated(EnumType.STRING) @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
    @Column(nullable = false, length = 32)
    private ImportTaskStage stage;
    private int attemptCount;
    private int maxAttempts;
    @Column(length = 128)
    private String ownerId;
    private long leaseGeneration;
    private LocalDateTime heartbeatAt;
    private LocalDateTime leaseUntil;
    @Column(nullable = false)
    private LocalDateTime nextAttemptAt;
    @Column(length = 128)
    private String lastErrorCode;
    @Column(length = 512)
    private String errorSummary;
    private boolean retryable;
    private Integer chunkCount;
    private Integer indexedChunks;
    @Column(nullable = false)
    private LocalDateTime createdAt;
    @Column(nullable = false)
    private LocalDateTime updatedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    public Long getId() { return id; }
    public void setId(Long value) { this.id = value; }
    public Document getDocument() { return document; }
    public void setDocument(Document value) { this.document = value; }
    public ImportTaskStatus getStatus() { return status; }
    public void setStatus(ImportTaskStatus value) { this.status = value; }
    public ImportTaskStage getStage() { return stage; }
    public void setStage(ImportTaskStage value) { this.stage = value; }
    public int getAttemptCount() { return attemptCount; }
    public void setAttemptCount(int value) { this.attemptCount = value; }
    public int getMaxAttempts() { return maxAttempts; }
    public void setMaxAttempts(int value) { this.maxAttempts = value; }
    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String value) { this.ownerId = value; }
    public long getLeaseGeneration() { return leaseGeneration; }
    public void setLeaseGeneration(long value) { this.leaseGeneration = value; }
    public LocalDateTime getHeartbeatAt() { return heartbeatAt; }
    public void setHeartbeatAt(LocalDateTime value) { this.heartbeatAt = value; }
    public LocalDateTime getLeaseUntil() { return leaseUntil; }
    public void setLeaseUntil(LocalDateTime value) { this.leaseUntil = value; }
    public LocalDateTime getNextAttemptAt() { return nextAttemptAt; }
    public void setNextAttemptAt(LocalDateTime value) { this.nextAttemptAt = value; }
    public String getLastErrorCode() { return lastErrorCode; }
    public void setLastErrorCode(String value) { this.lastErrorCode = value; }
    public String getErrorSummary() { return errorSummary; }
    public void setErrorSummary(String value) { this.errorSummary = value; }
    public boolean isRetryable() { return retryable; }
    public void setRetryable(boolean value) { this.retryable = value; }
    public Integer getChunkCount() { return chunkCount; }
    public void setChunkCount(Integer value) { this.chunkCount = value; }
    public Integer getIndexedChunks() { return indexedChunks; }
    public void setIndexedChunks(Integer value) { this.indexedChunks = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt = value; }
    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime value) { this.startedAt = value; }
    public LocalDateTime getFinishedAt() { return finishedAt; }
    public void setFinishedAt(LocalDateTime value) { this.finishedAt = value; }
}
