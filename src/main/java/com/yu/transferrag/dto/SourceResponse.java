package com.yu.transferrag.dto;

public class SourceResponse {

    private String citationId;
    private String title;
    private Long chunkId;
    private Long documentId;
    private Integer chunkIndex;
    private String sourceType;
    private boolean official;
    private String documentDepartment;
    private Integer documentYear;
    private String scope;
    private String chunkDepartment;
    private String major;
    private Integer policyYear;
    private Integer cohortYear;
    private Integer effectiveYear;
    private Double score;
    private boolean fileAvailable;
    private String sourceUrl;

    public String getCitationId() {
        return citationId;
    }

    public void setCitationId(String citationId) {
        this.citationId = citationId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
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

    public String getSourceType() {
        return sourceType;
    }

    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }

    public boolean isOfficial() {
        return official;
    }

    public void setOfficial(boolean official) {
        this.official = official;
    }

    public String getDocumentDepartment() {
        return documentDepartment;
    }

    public void setDocumentDepartment(String documentDepartment) {
        this.documentDepartment = documentDepartment;
    }

    public Integer getDocumentYear() {
        return documentYear;
    }

    public void setDocumentYear(Integer documentYear) {
        this.documentYear = documentYear;
    }

    public String getScope() {
        return scope;
    }

    public void setScope(String scope) {
        this.scope = scope;
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

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public boolean isFileAvailable() {
        return fileAvailable;
    }

    public void setFileAvailable(boolean fileAvailable) {
        this.fileAvailable = fileAvailable;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public void setSourceUrl(String sourceUrl) {
        this.sourceUrl = sourceUrl;
    }
}
