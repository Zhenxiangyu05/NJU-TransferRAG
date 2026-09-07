package com.yu.transferrag.controller;

import com.yu.transferrag.dto.SearchResultResponse;
import com.yu.transferrag.service.RetrievalService;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/test")
public class RetrievalController {

    private final RetrievalService retrievalService;

    public RetrievalController(RetrievalService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @GetMapping("/search")
    public ResponseEntity<List<SearchResultResponse>> search(
            @RequestParam @NotBlank(message = "query 不能为空") String query,
            @RequestParam(defaultValue = "3") @Positive(message = "topK 必须大于 0") int topK) {
        return ResponseEntity.ok(retrievalService.search(query, topK));
    }
}
