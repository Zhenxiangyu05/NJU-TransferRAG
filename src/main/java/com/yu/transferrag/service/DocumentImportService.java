package com.yu.transferrag.service;

import com.yu.transferrag.dto.*;
import com.yu.transferrag.entity.Document;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.nio.file.Path;
import java.util.*;

@Service
public class DocumentImportService {
    private final DocumentService documents;
    private final EvidenceRegistrationService registration;
    private final ImportTaskStateService state;
    private final ImportTaskFeatureGuard feature;
    public DocumentImportService(DocumentService documents, EvidenceRegistrationService registration,
                                 ImportTaskStateService state, ImportTaskFeatureGuard feature) {
        this.documents = documents; this.registration = registration; this.state = state; this.feature = feature;
    }
    public DocumentImportResponse importDocument(MultipartFile file, String title, String department, Integer year, String sourceType) {
        return importDocument(file, title, department, year, sourceType, Document.SCOPE_DEPARTMENT);
    }
    public DocumentImportResponse importDocument(MultipartFile file, String title, String department, Integer year, String sourceType, String scope) {
        ensureEnabled();
        validateMetadata(department, year, sourceType);
        Document candidate = documents.prepareEvidenceUpload(file, resolveTitle(title, file), department.trim(), year, sourceType.trim(), scope);
        EvidenceRegistrationService.Registration registered;
        try { registered = registration.register(candidate); }
        catch (DataIntegrityViolationException conflict) {
            // The failed registration transaction has already rolled back. Resolve the committed hash winner in a NEW transaction.
            registered = registration.resolveHash(candidate.getFileHash());
        }
        // Candidate files from failed/losing transactions are retained for explicit operator review.
        // Never delete an existing file, including after an ambiguous commit outcome.
        DocumentImportResponse response = state.view(registered.taskId(), registered.duplicate());
        if (registered.duplicate() && "FAILED".equals(response.getStatus()) && response.isRetryable()) {
            state.retry(registered.taskId());
            response = state.view(registered.taskId(), true);
        }
        return response;
    }
    public BatchDocumentImportResponse importBatch(List<MultipartFile> files, String department, Integer year, String sourceType) {
        ensureEnabled();
        if (files == null || files.isEmpty()) throw new IllegalArgumentException("files 不能为空");
        validateMetadata(department, year, sourceType);
        List<BatchDocumentImportItemResponse> items = new ArrayList<>();
        int succeeded = 0, failed = 0, pending = 0;
        for (MultipartFile file : files) {
            var item = new BatchDocumentImportItemResponse();
            item.setFileName(file == null ? null : file.getOriginalFilename());
            try {
                var response = importDocument(file, null, department, year, sourceType);
                item.setTaskId(response.getTaskId()); item.setStage(response.getStage());
                item.setDocumentId(response.getDocumentId()); item.setDuplicate(response.isDuplicate());
                item.setChunkCount(response.getChunkCount()); item.setIndexedChunks(response.getIndexedChunks());
                item.setStatus(response.getStatus()); item.setError(response.getError());
                if ("SUCCEEDED".equals(response.getStatus())) succeeded++;
                else if ("FAILED".equals(response.getStatus())) failed++;
                else pending++;
            } catch (RuntimeException failure) {
                item.setStatus("FAILED"); item.setError("文件登记失败，请检查输入或服务状态"); failed++;
            }
            items.add(item);
        }
        var result = new BatchDocumentImportResponse();
        result.setTotal(files.size()); result.setSuccess(succeeded); result.setFailed(failed); result.setPending(pending); result.setResults(items);
        return result;
    }
    private void ensureEnabled() {
        feature.requireEnabled();
    }
    private void validateMetadata(String department, Integer year, String sourceType) {
        if (department == null || department.isBlank()) throw new IllegalArgumentException("department 不能为空");
        if (year == null || year <= 0) throw new IllegalArgumentException("year 必须大于 0");
        if (sourceType == null || sourceType.isBlank()) throw new IllegalArgumentException("sourceType 不能为空");
    }
    private String resolveTitle(String title, MultipartFile file) {
        if (title != null && !title.isBlank()) return title.trim();
        if (file == null || file.getOriginalFilename() == null || file.getOriginalFilename().isBlank())
            throw new IllegalArgumentException("上传文件缺少文件名");
        String name = Path.of(file.getOriginalFilename().replace('\\', '/')).getFileName().toString();
        int dot = name.lastIndexOf('.');
        String generated = (dot > 0 ? name.substring(0, dot) : name).replace('_', ' ').replace('-', ' ').trim();
        return generated.isEmpty() ? name : generated;
    }
}
