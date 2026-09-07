package com.yu.transferrag.controller;

import com.yu.transferrag.dto.AskRequest;
import com.yu.transferrag.dto.RagResponse;
import com.yu.transferrag.service.RagService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
public class RagController {

    private final RagService ragService;

    public RagController(RagService ragService) {
        this.ragService = ragService;
    }

    @PostMapping("/ask")
    public ResponseEntity<RagResponse> ask(@Valid @RequestBody AskRequest request) {
        return ResponseEntity.ok(ragService.ask(request.getQuestion()));
    }
}
