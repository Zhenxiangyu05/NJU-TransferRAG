package com.yu.transferrag.dto;

public class SearchResultResponse {

    private String content;
    private Double score;
    private Long chunkId;
    private Long documentId;
    private Integer chunkIndex;
    private Integer policyYear;
    private Integer effectiveYear;
    private String chunkDepartment;
    private String major;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public Long getChunkId() {
        return chunkId;
    }

    public void setChunkId(Long chunkId) {
        this.chunkId = chunkId;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Integer getChunkIndex() {
        return chunkIndex;
    }

    public void setChunkIndex(Integer chunkIndex) {
        this.chunkIndex = chunkIndex;
    }

    public Integer getPolicyYear() {
        return policyYear;
    }

    public void setPolicyYear(Integer policyYear) {
        this.policyYear = policyYear;
    }

    public Integer getEffectiveYear() {
        return effectiveYear;
    }

    public void setEffectiveYear(Integer effectiveYear) {
        this.effectiveYear = effectiveYear;
    }

    public String getChunkDepartment() {
        return chunkDepartment;
    }

    public void setChunkDepartment(String chunkDepartment) {
        this.chunkDepartment = chunkDepartment;
    }

    public String getMajor() {
        return major;
    }

    public void setMajor(String major) {
        this.major = major;
    }
}
