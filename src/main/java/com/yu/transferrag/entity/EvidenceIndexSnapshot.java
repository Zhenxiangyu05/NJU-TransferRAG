package com.yu.transferrag.entity;

import jakarta.persistence.*;

/** Append-once input and append-once exact wire points. Never exposed by an API. */
@Entity
@Table(name = "evidence_index_snapshot")
public class EvidenceIndexSnapshot {
    @Id private Long taskId;
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "task_id", insertable = false, updatable = false,
        foreignKey = @ForeignKey(name = "fk_evidence_snapshot_task"))
    private ImportTask task;
    @Column(nullable = false, columnDefinition = "LONGTEXT", updatable = false) private String inputJson;
    @Column(nullable = false, length = 64, updatable = false) private String inputSha256;
    @Column(columnDefinition = "LONGTEXT") private String pointsJson;
    @Column(length = 64) private String pointsSha256;
    public EvidenceIndexSnapshot() {}
    public EvidenceIndexSnapshot(Long taskId, String input, String hash) {
        this.taskId=taskId; this.inputJson=input; this.inputSha256=hash;
    }
    public Long getTaskId() { return taskId; }
    public String getInputJson() { return inputJson; }
    public String getInputSha256() { return inputSha256; }
    public String getPointsJson() { return pointsJson; }
    public String getPointsSha256() { return pointsSha256; }
    public void freezePoints(String points, String hash) {
        if (pointsJson != null) throw new IllegalStateException("Points already frozen");
        pointsJson=points; pointsSha256=hash;
    }
}
