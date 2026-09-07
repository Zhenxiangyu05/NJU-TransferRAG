package com.yu.transferrag.controller;

import com.yu.transferrag.dto.CreateDocumentRequest;
import com.yu.transferrag.dto.DocumentResponse;
import com.yu.transferrag.service.ChunkService;
import com.yu.transferrag.service.DocumentParserService;
import com.yu.transferrag.service.DocumentService;
import com.yu.transferrag.service.VectorIndexService;
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

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class    DocumentController {

    private final DocumentService documentService;
    private final DocumentParserService documentParserService;
    private final ChunkService chunkService;
    private final VectorIndexService vectorIndexService;

    public DocumentController(DocumentService documentService,
                              DocumentParserService documentParserService,
                              ChunkService chunkService,
                              VectorIndexService vectorIndexService) {
        this.documentService = documentService;
        this.documentParserService = documentParserService;
        this.chunkService = chunkService;
        this.vectorIndexService = vectorIndexService;
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
}
