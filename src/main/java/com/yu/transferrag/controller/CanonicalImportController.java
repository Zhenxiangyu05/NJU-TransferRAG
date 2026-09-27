package com.yu.transferrag.controller;

import com.yu.transferrag.dto.CanonicalImportRequest;
import com.yu.transferrag.dto.CanonicalImportResponse;
import com.yu.transferrag.service.CanonicalImportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/documents/canonical")
public class CanonicalImportController {

    private final CanonicalImportService canonicalImportService;

    public CanonicalImportController(CanonicalImportService canonicalImportService) {
        this.canonicalImportService = canonicalImportService;
    }

    @PostMapping
    public ResponseEntity<?> importCanonical(@Valid @RequestBody CanonicalImportRequest request) {
        try {
            CanonicalImportResponse response = canonicalImportService.importCanonical(request);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.badRequest().body(Map.of("error", exception.getMessage()));
        }
    }
}
