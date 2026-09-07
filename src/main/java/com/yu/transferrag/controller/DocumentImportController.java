package com.yu.transferrag.controller;

import com.yu.transferrag.dto.BatchDocumentImportResponse;
import com.yu.transferrag.dto.DocumentImportResponse;
import com.yu.transferrag.service.DocumentImportService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents/import")
public class DocumentImportController {

    private final DocumentImportService documentImportService;

    public DocumentImportController(DocumentImportService documentImportService) {
        this.documentImportService = documentImportService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importDocument(@RequestParam MultipartFile file,
                                            @RequestParam(required = false) String title,
                                            @RequestParam String department,
                                            @RequestParam Integer year,
                                            @RequestParam String sourceType) {
        try {
            DocumentImportResponse response = documentImportService.importDocument(
                    file, title, department, year, sourceType
            );
            HttpStatus status = response.isDuplicate() ? HttpStatus.OK : HttpStatus.CREATED;
            return ResponseEntity.status(status).body(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorResponse(e));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse(e));
        }
    }

    @PostMapping(value = "/batch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> importBatch(@RequestParam List<MultipartFile> files,
                                         @RequestParam String department,
                                         @RequestParam Integer year,
                                         @RequestParam String sourceType) {
        try {
            BatchDocumentImportResponse response = documentImportService.importBatch(
                    files, department, year, sourceType
            );
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(errorResponse(e));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(errorResponse(e));
        }
    }

    private Map<String, String> errorResponse(RuntimeException exception) {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("status", "FAILED");
        response.put("error", exception.getMessage());
        return response;
    }
}
