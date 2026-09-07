package com.yu.transferrag.dto;

import java.util.List;

public class BatchDocumentImportResponse {

    private Integer total;
    private Integer success;
    private Integer failed;
    private List<BatchDocumentImportItemResponse> results;

    public Integer getTotal() {
        return total;
    }

    public void setTotal(Integer total) {
        this.total = total;
    }

    public Integer getSuccess() {
        return success;
    }

    public void setSuccess(Integer success) {
        this.success = success;
    }

    public Integer getFailed() {
        return failed;
    }

    public void setFailed(Integer failed) {
        this.failed = failed;
    }

    public List<BatchDocumentImportItemResponse> getResults() {
        return results;
    }

    public void setResults(List<BatchDocumentImportItemResponse> results) {
        this.results = results;
    }
}
