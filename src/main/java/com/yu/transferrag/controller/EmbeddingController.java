package com.yu.transferrag.controller;

import com.yu.transferrag.dto.EmbeddingTestResponse;
import jakarta.validation.constraints.NotBlank;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
public class EmbeddingController {

    private final EmbeddingModel embeddingModel;

    public EmbeddingController(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    @GetMapping("/embedding")
    public ResponseEntity<EmbeddingTestResponse> createEmbedding(
            @RequestParam @NotBlank(message = "text 不能为空") String text) {
        float[] embedding = embeddingModel.embed(text);
        float[] preview = Arrays.copyOf(embedding, Math.min(10, embedding.length));

        EmbeddingTestResponse response = new EmbeddingTestResponse(
                text,
                embedding.length,
                preview
        );
        return ResponseEntity.ok(response);
    }

    @GetMapping("/similarity")
    public ResponseEntity<Map<String, Object>> compareSimilarity() {
        String textA = "软件学院转专业考试要求";
        String textB = "转软院需要参加什么考核？";
        String textC = "食堂今天有什么好吃的？";

        float[] vectorA = embeddingModel.embed(textA);
        float[] vectorB = embeddingModel.embed(textB);
        float[] vectorC = embeddingModel.embed(textC);

        double similarityAB = cosineSimilarity(vectorA, vectorB);
        double similarityAC = cosineSimilarity(vectorA, vectorC);

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("textA", textA);
        response.put("textB", textB);
        response.put("textC", textC);
        response.put("similarityAB", similarityAB);
        response.put("similarityAC", similarityAC);
        return ResponseEntity.ok(response);
    }

    private double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("两个向量的维度必须一致");
        }

        double dotProduct = 0.0;
        double normA = 0.0;
        double normB = 0.0;

        for (int i = 0; i < a.length; i++) {
            dotProduct += a[i] * b[i];
            normA += a[i] * a[i];
            normB += b[i] * b[i];
        }

        if (normA == 0.0 || normB == 0.0) {
            throw new IllegalArgumentException("不能计算零向量的余弦相似度");
        }

        return dotProduct / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
