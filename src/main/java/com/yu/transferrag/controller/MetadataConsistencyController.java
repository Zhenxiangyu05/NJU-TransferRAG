package com.yu.transferrag.controller;

import com.yu.transferrag.dto.MetadataConsistencyReport;
import com.yu.transferrag.service.MetadataConsistencyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/test/metadata-consistency")
public class MetadataConsistencyController {

    private final MetadataConsistencyService metadataConsistencyService;

    public MetadataConsistencyController(MetadataConsistencyService metadataConsistencyService) {
        this.metadataConsistencyService = metadataConsistencyService;
    }

    @GetMapping("/{documentId}")
    public ResponseEntity<?> check(@PathVariable Long documentId) {
        try {
            MetadataConsistencyReport report = metadataConsistencyService.check(documentId);
            return ResponseEntity.ok(report);
        } catch (IllegalArgumentException exception) {
            return ResponseEntity.notFound().build();
        } catch (IllegalStateException exception) {
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "ERROR",
                    "error", exception.getMessage()
            ));
        }
    }
}
