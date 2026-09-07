package com.yu.transferrag.service;

import com.yu.transferrag.dto.BatchDocumentImportItemResponse;
import com.yu.transferrag.dto.BatchDocumentImportResponse;
import com.yu.transferrag.dto.DocumentImportResponse;
import com.yu.transferrag.dto.DocumentResponse;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocumentImportService {

    private static final String SUCCESS = "SUCCESS";
    private static final String FAILED = "FAILED";

    private final DocumentService documentService;
    private final ChunkService chunkService;
    private final VectorIndexService vectorIndexService;

    public DocumentImportService(DocumentService documentService,
                                 ChunkService chunkService,
                                 VectorIndexService vectorIndexService) {
        this.documentService = documentService;
        this.chunkService = chunkService;
        this.vectorIndexService = vectorIndexService;
    }

    public DocumentImportResponse importDocument(MultipartFile file,
                                                 String title,
                                                 String department,
                                                 Integer year,
                                                 String sourceType) {
        validateMetadata(department, year, sourceType);
        String resolvedTitle = resolveTitle(title, file);

        DocumentService.UploadResult uploadResult;
        try {
            uploadResult = documentService.uploadDocumentIfAbsent(
                    file,
                    resolvedTitle,
                    department.trim(),
                    year,
                    sourceType.trim()
            );
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (RuntimeException e) {
            throw stageFailure("文件保存", e);
        }

        DocumentResponse savedDocument = uploadResult.document();
        if (uploadResult.duplicate()) {
            return duplicateResponse(savedDocument);
        }

        int chunkCount;
        try {
            chunkCount = chunkService.createChunks(savedDocument.getId());
        } catch (RuntimeException e) {
            throw stageFailure("Chunk 创建", e);
        }

        int indexedChunks;
        try {
            indexedChunks = vectorIndexService.indexDocument(savedDocument.getId());
        } catch (RuntimeException e) {
            throw stageFailure("Qdrant 索引", e);
        }

        DocumentImportResponse response = new DocumentImportResponse();
        response.setDocumentId(savedDocument.getId());
        response.setTitle(savedDocument.getTitle());
        response.setDepartment(savedDocument.getDepartment());
        response.setYear(savedDocument.getYear());
        response.setSourceType(savedDocument.getSourceType());
        response.setChunkCount(chunkCount);
        response.setIndexedChunks(indexedChunks);
        response.setStatus(SUCCESS);
        response.setDuplicate(false);
        return response;
    }

    public BatchDocumentImportResponse importBatch(List<MultipartFile> files,
                                                   String department,
                                                   Integer year,
                                                   String sourceType) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException("files 不能为空");
        }
        validateMetadata(department, year, sourceType);

        List<BatchDocumentImportItemResponse> results = new ArrayList<>();
        int successCount = 0;

        for (MultipartFile file : files) {
            try {
                DocumentImportResponse imported = importDocument(
                        file,
                        null,
                        department,
                        year,
                        sourceType
                );
                results.add(toBatchSuccess(file, imported));
                successCount++;
            } catch (RuntimeException e) {
                results.add(toBatchFailure(file, e));
            }
        }

        BatchDocumentImportResponse response = new BatchDocumentImportResponse();
        response.setTotal(files.size());
        response.setSuccess(successCount);
        response.setFailed(files.size() - successCount);
        response.setResults(results);
        return response;
    }

    private BatchDocumentImportItemResponse toBatchSuccess(MultipartFile file,
                                                           DocumentImportResponse imported) {
        BatchDocumentImportItemResponse item = new BatchDocumentImportItemResponse();
        item.setFileName(getOriginalFileName(file));
        item.setDocumentId(imported.getDocumentId());
        item.setChunkCount(imported.getChunkCount());
        item.setIndexedChunks(imported.getIndexedChunks());
        item.setStatus(SUCCESS);
        item.setDuplicate(imported.isDuplicate());
        return item;
    }

    private DocumentImportResponse duplicateResponse(DocumentResponse existingDocument) {
        DocumentImportResponse response = new DocumentImportResponse();
        response.setDocumentId(existingDocument.getId());
        response.setTitle(existingDocument.getTitle());
        response.setDepartment(existingDocument.getDepartment());
        response.setYear(existingDocument.getYear());
        response.setSourceType(existingDocument.getSourceType());
        response.setChunkCount(0);
        response.setIndexedChunks(0);
        response.setStatus(SUCCESS);
        response.setDuplicate(true);
        return response;
    }

    private BatchDocumentImportItemResponse toBatchFailure(MultipartFile file,
                                                           RuntimeException exception) {
        BatchDocumentImportItemResponse item = new BatchDocumentImportItemResponse();
        item.setFileName(getOriginalFileName(file));
        item.setStatus(FAILED);
        item.setError(errorMessage(exception));
        return item;
    }

    private void validateMetadata(String department, Integer year, String sourceType) {
        if (department == null || department.isBlank()) {
            throw new IllegalArgumentException("department 不能为空");
        }
        if (year == null || year <= 0) {
            throw new IllegalArgumentException("year 必须大于 0");
        }
        if (sourceType == null || sourceType.isBlank()) {
            throw new IllegalArgumentException("sourceType 不能为空");
        }
    }

    private String resolveTitle(String title, MultipartFile file) {
        if (title != null && !title.isBlank()) {
            return title.trim();
        }

        String fileName = getOriginalFileName(file);
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("上传文件缺少文件名，无法生成 title");
        }

        String simpleFileName = Path.of(fileName).getFileName().toString();
        int dotIndex = simpleFileName.lastIndexOf('.');
        String generatedTitle = dotIndex > 0
                ? simpleFileName.substring(0, dotIndex)
                : simpleFileName;
        generatedTitle = generatedTitle.replace('_', ' ')
                .replace('-', ' ')
                .trim();
        return generatedTitle.isEmpty() ? simpleFileName : generatedTitle;
    }

    private String getOriginalFileName(MultipartFile file) {
        return file == null ? null : file.getOriginalFilename();
    }

    private IllegalStateException stageFailure(String stage, RuntimeException cause) {
        return new IllegalStateException(
                "文档导入在【" + stage + "】阶段失败: " + errorMessage(cause),
                cause
        );
    }

    private String errorMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message;
    }
}
