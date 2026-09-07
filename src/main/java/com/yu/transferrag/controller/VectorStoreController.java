package com.yu.transferrag.controller;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class VectorStoreController {

    private final VectorStore vectorStore;

    public VectorStoreController(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @GetMapping("/vector-store")
    public ResponseEntity<Map<String, String>> testVectorStore() {
        return ResponseEntity.ok(Map.of(
                "status", "ok",
                "message", "VectorStore initialized"
        ));
    }
}
