package com.yu.transferrag.service;

import com.yu.transferrag.entity.Document;
import com.yu.transferrag.repository.DocumentRepository;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.Locale;

@Service
public class DocumentParserService {

    private final DocumentRepository documentRepository;
    private final PdfParserService pdfParserService;
    private final PlainTextParserService plainTextParserService;
    private final DocxParserService docxParserService;

    public DocumentParserService(DocumentRepository documentRepository,
                                 PdfParserService pdfParserService,
                                 PlainTextParserService plainTextParserService,
                                 DocxParserService docxParserService) {
        this.documentRepository = documentRepository;
        this.pdfParserService = pdfParserService;
        this.plainTextParserService = plainTextParserService;
        this.docxParserService = docxParserService;
    }

    public String extractText(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在"));

        String extension = getExtension(document.getOriginalFileName());
        if (extension == null) {
            extension = getExtension(document.getFilePath());
        }

        if (extension != null) {
            return extractByExtension(documentId, document, extension);
        }
        return extractByContentType(documentId, document);
    }

    private String extractByExtension(Long documentId, Document document, String extension) {
        return switch (extension) {
            case "pdf" -> pdfParserService.extractText(documentId);
            case "md", "txt" -> plainTextParserService.extractText(getFilePath(document));
            case "docx" -> docxParserService.extractText(getFilePath(document));
            default -> throw unsupportedType();
        };
    }

    private String extractByContentType(Long documentId, Document document) {
        String contentType = document.getContentType();
        if (contentType == null || contentType.isBlank()) {
            throw unsupportedType();
        }

        String normalizedContentType = contentType.toLowerCase(Locale.ROOT)
                .split(";", 2)[0]
                .trim();
        return switch (normalizedContentType) {
            case "application/pdf" -> pdfParserService.extractText(documentId);
            case "text/markdown", "text/x-markdown", "text/plain" ->
                    plainTextParserService.extractText(getFilePath(document));
            case "application/vnd.openxmlformats-officedocument.wordprocessingml.document" ->
                    docxParserService.extractText(getFilePath(document));
            default -> throw unsupportedType();
        };
    }

    private Path getFilePath(Document document) {
        String filePath = document.getFilePath();
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalStateException("文档文件路径不存在");
        }
        return Path.of(filePath);
    }

    private String getExtension(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }

        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private IllegalArgumentException unsupportedType() {
        return new IllegalArgumentException("不支持的文档类型，仅支持 .pdf、.md、.txt、.docx 文件");
    }
}
