package com.yu.transferrag.service;

import com.yu.transferrag.dto.CreateDocumentRequest;
import com.yu.transferrag.dto.DocumentResponse;
import com.yu.transferrag.entity.Document;
import com.yu.transferrag.repository.DocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class DocumentService {

    private static final Set<String> SUPPORTED_EXTENSIONS = Set.of("pdf", "md", "txt", "docx");

    private final DocumentRepository documentRepository;
    private final String uploadDir;

    public DocumentService(DocumentRepository documentRepository,
                           @Value("${app.upload-dir}") String uploadDir) {
        this.documentRepository = documentRepository;
        this.uploadDir = uploadDir;
    }

    public DocumentResponse create(CreateDocumentRequest request) {
        Document document = new Document();
        document.setTitle(request.getTitle());
        document.setDepartment(request.getDepartment());
        document.setYear(request.getYear());
        document.setSourceType(request.getSourceType());
        document.setSourceUrl(request.getSourceUrl());

        Document savedDocument = documentRepository.save(document);
        return toResponse(savedDocument);
    }

    public Optional<DocumentResponse> findById(Long id) {
        return documentRepository.findById(id)
                .map(this::toResponse);
    }

    public DocumentResponse uploadDocument(MultipartFile file,
                                           String title,
                                           String department,
                                           Integer year,
                                           String sourceType) {
        return uploadDocumentIfAbsent(file, title, department, year, sourceType).document();
    }

    public UploadResult uploadDocumentIfAbsent(MultipartFile file,
                                               String title,
                                               String department,
                                               Integer year,
                                               String sourceType) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        String originalFileName = file.getOriginalFilename();
        String extension = getSupportedExtension(originalFileName);
        String fileHash = calculateSha256(file);

        Optional<Document> existingDocument = documentRepository.findByFileHash(fileHash);
        if (existingDocument.isPresent()) {
            return new UploadResult(toResponse(existingDocument.get()), true);
        }

        Path uploadPath = Path.of(uploadDir);
        String storedFileName = UUID.randomUUID() + "." + extension;
        Path targetPath = uploadPath.resolve(storedFileName);

        try {
            Files.createDirectories(uploadPath);
            Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new IllegalStateException("保存文档文件失败: " + originalFileName, e);
        }

        Document document = new Document();
        document.setTitle(title);
        document.setDepartment(department);
        document.setYear(year);
        document.setSourceType(sourceType);
        document.setFilePath(targetPath.toString());
        document.setOriginalFileName(originalFileName);
        document.setFileSize(file.getSize());
        document.setContentType(file.getContentType());
        document.setFileHash(fileHash);
        document.setStatus("UPLOADED");

        try {
            Document savedDocument = documentRepository.save(document);
            return new UploadResult(toResponse(savedDocument), false);
        } catch (DataIntegrityViolationException e) {
            deleteStoredFile(targetPath, e);
            return documentRepository.findByFileHash(fileHash)
                    .map(existing -> new UploadResult(toResponse(existing), true))
                    .orElseThrow(() -> e);
        } catch (RuntimeException e) {
            deleteStoredFile(targetPath, e);
            throw e;
        }
    }

    public String calculateSha256(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("上传文件不能为空");
        }

        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", e);
        }

        try (InputStream inputStream = file.getInputStream()) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (IOException e) {
            throw new IllegalStateException("计算文件 SHA-256 失败: " + file.getOriginalFilename(), e);
        }
    }

    private void deleteStoredFile(Path path, RuntimeException cause) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException cleanupException) {
            cause.addSuppressed(cleanupException);
        }
    }

    private String getSupportedExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            throw new IllegalArgumentException("上传文件缺少文件名");
        }

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            throw new IllegalArgumentException("不支持的文件类型，仅允许上传 .pdf、.md、.txt、.docx 文件");
        }

        String extension = fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
        if (!SUPPORTED_EXTENSIONS.contains(extension)) {
            throw new IllegalArgumentException("不支持的文件类型，仅允许上传 .pdf、.md、.txt、.docx 文件");
        }
        return extension;
    }

    private DocumentResponse toResponse(Document document) {
        DocumentResponse response = new DocumentResponse();
        response.setId(document.getId());
        response.setTitle(document.getTitle());
        response.setDepartment(document.getDepartment());
        response.setYear(document.getYear());
        response.setSourceType(document.getSourceType());
        response.setSourceUrl(document.getSourceUrl());
        response.setFilePath(document.getFilePath());
        response.setOriginalFileName(document.getOriginalFileName());
        response.setFileSize(document.getFileSize());
        response.setContentType(document.getContentType());
        response.setStatus(document.getStatus());
        response.setCreatedAt(document.getCreatedAt());
        return response;
    }

    public record UploadResult(DocumentResponse document, boolean duplicate) {
    }
}
