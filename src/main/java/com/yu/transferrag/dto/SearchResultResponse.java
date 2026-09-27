package com.yu.transferrag.dto;

import com.yu.transferrag.entity.DocumentRole;

public class SearchResultResponse {

    private String content;
    private Double score;
    private Long chunkId;
    private Long documentId;
    private Integer chunkIndex;
    private Integer policyYear;
    private Integer cohortYear;
    private Integer effectiveYear;
    private String chunkDepartment;
    private String major;
    private DocumentRole documentRole;
    private String section;
    private String retrievalLayer;
    private String documentDepartment;
    private String scope;
    private String sourceType;

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

    public Integer getCohortYear() {
        return cohortYear;
    }

    public void setCohortYear(Integer cohortYear) {
        this.cohortYear = cohortYear;
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

    public DocumentRole getDocumentRole() {
        return documentRole;
    }

    public void setDocumentRole(DocumentRole documentRole) {
        this.documentRole = documentRole;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getRetrievalLayer() {
        return retrievalLayer;
    }

    public void setRetrievalLayer(String retrievalLayer) {
        this.retrievalLayer = retrievalLayer;
    }

    public String getDocumentDepartment() {
        return documentDepartment;
    }

    public void setDocumentDepartment(String documentDepartment) {
        this.documentDepartment = documentDepartment;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
    }

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }
}
