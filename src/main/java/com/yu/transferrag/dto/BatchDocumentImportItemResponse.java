package com.yu.transferrag.dto;

public class BatchDocumentImportItemResponse {

    private String fileName;
    private Long documentId;
    private Integer chunkCount;
    private Integer indexedChunks;
    private String status;
    private String error;
    private boolean duplicate;

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public Long getDocumentId() {
        return documentId;
    }

    public void setDocumentId(Long documentId) {
        this.documentId = documentId;
    }

    public Integer getChunkCount() {
        return chunkCount;
    }

    public void setChunkCount(Integer chunkCount) {
        this.chunkCount = chunkCount;
    }

    public Integer getIndexedChunks() {
        return indexedChunks;
    }

    public void setIndexedChunks(Integer indexedChunks) {
        this.indexedChunks = indexedChunks;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public boolean isDuplicate() {
        return duplicate;
    }

    public void setDuplicate(boolean duplicate) {
        this.duplicate = duplicate;
    }
}
