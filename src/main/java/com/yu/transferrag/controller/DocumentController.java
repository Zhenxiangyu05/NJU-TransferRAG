package com.yu.transferrag.controller;

import com.yu.transferrag.dto.CreateDocumentRequest;
import com.yu.transferrag.dto.DocumentResponse;
import com.yu.transferrag.entity.Document;
import com.yu.transferrag.repository.DocumentRepository;
import com.yu.transferrag.service.ChunkService;
import com.yu.transferrag.service.DocumentParserService;
import com.yu.transferrag.service.DocumentService;
import com.yu.transferrag.service.VectorIndexService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@RestController
@RequestMapping("/api/documents")
public class    DocumentController {

    private final DocumentService documentService;
    private final DocumentParserService documentParserService;
    private final ChunkService chunkService;
    private final VectorIndexService vectorIndexService;
    private final DocumentRepository documentRepository;

    public DocumentController(DocumentService documentService,
                              DocumentParserService documentParserService,
                              ChunkService chunkService,
                              VectorIndexService vectorIndexService,
                              DocumentRepository documentRepository) {
        this.documentService = documentService;
        this.documentParserService = documentParserService;
        this.chunkService = chunkService;
        this.vectorIndexService = vectorIndexService;
        this.documentRepository = documentRepository;
    }

    @PostMapping
    public ResponseEntity<DocumentResponse> create(@Valid @RequestBody CreateDocumentRequest request) {
        DocumentResponse response = documentService.create(request);
        return ResponseEntity.status(201).body(response);
    }

    @PostMapping("/upload")
    public ResponseEntity<?> uploadDocument(@RequestParam MultipartFile file,
                                            @RequestParam String title,
                                            @RequestParam String department,
                                            @RequestParam Integer year,
                                            @RequestParam String sourceType) {
        try {
            DocumentResponse response = documentService.uploadDocument(
                    file, title, department, year, sourceType
            );
            return ResponseEntity.status(201).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/{id}/text")
    public ResponseEntity<String> extractText(@PathVariable Long id) {
        String text = documentParserService.extractText(id);
        return ResponseEntity.ok(text);
    }

    @PostMapping("/{id}/chunks")
    public ResponseEntity<Integer> createChunks(@PathVariable Long id) {
        int chunkCount = chunkService.createChunks(id);
        return ResponseEntity.status(201).body(chunkCount);
    }

    @PostMapping("/{id}/index")
    public ResponseEntity<Map<String, Object>> indexDocument(@PathVariable Long id) {
        int indexedChunks = vectorIndexService.indexDocument(id);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("documentId", id);
        response.put("indexedChunks", indexedChunks);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<DocumentResponse> findById(@PathVariable Long id) {
        return documentService.findById(id)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> getOriginalFile(@PathVariable Long id) {
        return documentRepository.findById(id)
                .flatMap(this::createFileResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private Optional<ResponseEntity<Resource>> createFileResponse(Document document) {
        if (document.getFilePath() == null || document.getFilePath().isBlank()) {
            return Optional.empty();
        }

        Path path;
        try {
            path = Path.of(document.getFilePath()).toAbsolutePath().normalize();
        } catch (RuntimeException exception) {
            return Optional.empty();
        }
        if (!Files.isRegularFile(path)) {
            return Optional.empty();
        }

        String extension = resolveExtension(document, path);
        if (extension == null) {
            return Optional.empty();
        }

        String fileName = safeFileName(document.getOriginalFileName(), path);
        String dispositionType = "docx".equals(extension) ? "attachment" : "inline";
        ContentDisposition disposition = ContentDisposition.builder(dispositionType)
                .filename(fileName, StandardCharsets.UTF_8)
                .build();

        Resource resource = new FileSystemResource(path);
        return Optional.of(ResponseEntity.ok()
                .contentType(resolveMediaType(document.getContentType(), extension))
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .body(resource));
    }

    private String resolveExtension(Document document, Path path) {
        Set<String> supportedExtensions = Set.of("pdf", "docx", "md", "txt");
        String extension = extensionOf(document.getOriginalFileName());
        if (extension == null || !supportedExtensions.contains(extension)) {
            extension = extensionOf(path.getFileName().toString());
        }
        return extension != null && supportedExtensions.contains(extension) ? extension : null;
    }

    private String extensionOf(String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return null;
        }
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex < 0 || dotIndex == fileName.length() - 1) {
            return null;
        }
        return fileName.substring(dotIndex + 1).toLowerCase(Locale.ROOT);
    }

    private String safeFileName(String originalFileName, Path path) {
        String fileName = originalFileName == null || originalFileName.isBlank()
                ? path.getFileName().toString()
                : originalFileName;
        return fileName.replace('\r', '_').replace('\n', '_');
    }

    private MediaType resolveMediaType(String contentType, String extension) {
        if (contentType != null && !contentType.isBlank()
                && !MediaType.APPLICATION_OCTET_STREAM_VALUE.equalsIgnoreCase(contentType.trim())) {
            try {
                MediaType parsedType = MediaType.parseMediaType(contentType);
                if (isReliableContentType(parsedType, extension)) {
                    if (("md".equals(extension) || "txt".equals(extension))
                            && parsedType.getCharset() == null) {
                        return new MediaType(parsedType, StandardCharsets.UTF_8);
                    }
                    return parsedType;
                }
            } catch (IllegalArgumentException ignored) {
                // Fall through to the extension-based type below.
            }
        }

        return switch (extension) {
            case "pdf" -> MediaType.APPLICATION_PDF;
            case "docx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            );
            case "md" -> MediaType.parseMediaType("text/markdown; charset=UTF-8");
            case "txt" -> MediaType.parseMediaType("text/plain; charset=UTF-8");
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    private boolean isReliableContentType(MediaType contentType, String extension) {
        return switch (extension) {
            case "pdf" -> MediaType.APPLICATION_PDF.isCompatibleWith(contentType);
            case "docx" -> MediaType.parseMediaType(
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            ).isCompatibleWith(contentType);
            case "md" -> MediaType.parseMediaType("text/markdown").isCompatibleWith(contentType)
                    || MediaType.TEXT_PLAIN.isCompatibleWith(contentType);
            case "txt" -> MediaType.TEXT_PLAIN.isCompatibleWith(contentType);
            default -> false;
        };
    }
}
