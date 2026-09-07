package com.yu.transferrag.service;

import com.yu.transferrag.entity.Chunk;
import com.yu.transferrag.repository.ChunkRepository;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class VectorIndexService {

    private final ChunkRepository chunkRepository;
    private final VectorStore vectorStore;

    public VectorIndexService(ChunkRepository chunkRepository, VectorStore vectorStore) {
        this.chunkRepository = chunkRepository;
        this.vectorStore = vectorStore;
    }

    public int indexDocument(Long documentId) {
        Filter.Expression documentFilter = new Filter.Expression(
                Filter.ExpressionType.EQ,
                new Filter.Key("documentId"),
                new Filter.Value(documentId.toString())
        );
        vectorStore.delete(documentFilter);

        List<Chunk> chunks = chunkRepository
                .findByDocument_IdOrderByChunkIndexAsc(documentId);

        if (chunks.isEmpty()) {
            throw new IllegalStateException("该文档没有 Chunk，请先完成文本切分");
        }

        List<org.springframework.ai.document.Document> vectorDocuments = chunks.stream()
                .map(this::toVectorDocument)
                .toList();

        vectorStore.add(vectorDocuments);
        return vectorDocuments.size();
    }

    private org.springframework.ai.document.Document toVectorDocument(Chunk chunk) {
        com.yu.transferrag.entity.Document sourceDocument = chunk.getDocument();
        var builder = org.springframework.ai.document.Document.builder()
                .text(chunk.getContent())
                .metadata("chunkId", chunk.getId().toString())
                .metadata("documentId", sourceDocument.getId().toString())
                .metadata("chunkIndex", chunk.getChunkIndex());

        if (sourceDocument.getDepartment() != null) {
            builder.metadata("department", sourceDocument.getDepartment());
        }
        if (sourceDocument.getYear() != null) {
            builder.metadata("year", sourceDocument.getYear());
        }
        if (sourceDocument.getSourceType() != null) {
            builder.metadata("sourceType", sourceDocument.getSourceType());
        }
        if (chunk.getPolicyYear() != null) {
            builder.metadata("policyYear", chunk.getPolicyYear());
        }
        if (chunk.getDepartment() != null) {
            builder.metadata("chunkDepartment", chunk.getDepartment());
        }
        if (chunk.getMajor() != null) {
            builder.metadata("major", chunk.getMajor());
        }
        Integer effectiveYear = chunk.getPolicyYear() != null
                ? chunk.getPolicyYear()
                : sourceDocument.getYear();
        if (effectiveYear != null) {
            builder.metadata("effectiveYear", effectiveYear);
        }
        builder.metadata("scope", normalizedScope(sourceDocument.getScope()));
        return builder.build();
    }

    private String normalizedScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return com.yu.transferrag.entity.Document.SCOPE_DEPARTMENT;
        }
        return scope.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
