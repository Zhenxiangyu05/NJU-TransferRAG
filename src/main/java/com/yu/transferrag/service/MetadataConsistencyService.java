package com.yu.transferrag.service;

import com.yu.transferrag.dto.MetadataConsistencyReport;
import com.yu.transferrag.entity.Chunk;
import com.yu.transferrag.entity.Document;
import com.yu.transferrag.repository.ChunkRepository;
import com.yu.transferrag.repository.DocumentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

@Service
public class MetadataConsistencyService {

    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final QdrantMetadataReader qdrantMetadataReader;

    public MetadataConsistencyService(DocumentRepository documentRepository,
                                      ChunkRepository chunkRepository,
                                      QdrantMetadataReader qdrantMetadataReader) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.qdrantMetadataReader = qdrantMetadataReader;
    }

    @Transactional(readOnly = true)
    public MetadataConsistencyReport check(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在: " + documentId));
        List<Chunk> chunks = chunkRepository.findByDocument_IdOrderByChunkIndexAsc(documentId);
        List<QdrantMetadataReader.QdrantMetadataPoint> points =
                qdrantMetadataReader.findByDocumentId(documentId);

        Set<String> allIssues = new LinkedHashSet<>();
        Set<String> allWarnings = new LinkedHashSet<>();
        Map<Long, List<QdrantMetadataReader.QdrantMetadataPoint>> pointsByChunk =
                groupPointsByChunk(points, allIssues);
        Map<Long, Chunk> chunksById = new LinkedHashMap<>();
        for (Chunk chunk : chunks) {
            chunksById.put(chunk.getId(), chunk);
        }

        for (QdrantMetadataReader.QdrantMetadataPoint point : points) {
            checkDocumentMetadata(document, point, allIssues);
        }
        for (Long qdrantChunkId : pointsByChunk.keySet()) {
            if (!chunksById.containsKey(qdrantChunkId)) {
                allIssues.add("QDRANT_EXTRA point references unknown chunkId=" + qdrantChunkId);
            }
        }

        List<MetadataConsistencyReport.ChunkConsistency> chunkReports = new ArrayList<>();
        for (Chunk chunk : chunks) {
            List<String> chunkIssues = new ArrayList<>();
            List<String> chunkWarnings = new ArrayList<>();
            List<QdrantMetadataReader.QdrantMetadataPoint> chunkPoints =
                    pointsByChunk.getOrDefault(chunk.getId(), List.of());

            if (chunk.getDepartment() == null || chunk.getDepartment().isBlank()) {
                chunkWarnings.add("MYSQL_MISSING chunk.department");
            }
            if (chunk.getMajor() == null || chunk.getMajor().isBlank()) {
                chunkWarnings.add("MYSQL_MISSING chunk.major");
            }
            if (chunkPoints.isEmpty()) {
                chunkIssues.add("QDRANT_MISSING point for chunkId=" + chunk.getId());
            } else if (chunkPoints.size() > 1) {
                chunkIssues.add("QDRANT_DUPLICATE " + chunkPoints.size()
                        + " points for chunkId=" + chunk.getId());
            }

            for (QdrantMetadataReader.QdrantMetadataPoint point : chunkPoints) {
                checkChunkMetadata(document, chunk, point, chunkIssues);
            }

            chunkIssues.forEach(issue -> allIssues.add("chunkId=" + chunk.getId() + " " + issue));
            chunkWarnings.forEach(warning -> allWarnings.add(
                    "chunkId=" + chunk.getId() + " " + warning
            ));
            chunkReports.add(new MetadataConsistencyReport.ChunkConsistency(
                    chunk.getId(),
                    chunk.getChunkIndex(),
                    mysqlChunkSnapshot(document, chunk),
                    chunkPoints.stream().map(this::qdrantSnapshot).toList(),
                    List.copyOf(chunkIssues),
                    List.copyOf(chunkWarnings)
            ));
        }

        String status = allIssues.isEmpty() ? "OK" : "MISMATCH";
        return new MetadataConsistencyReport(
                status,
                documentId,
                mysqlDocumentSnapshot(document),
                chunks.size(),
                points.size(),
                List.copyOf(allIssues),
                List.copyOf(allWarnings),
                List.copyOf(chunkReports)
        );
    }

    private Map<Long, List<QdrantMetadataReader.QdrantMetadataPoint>> groupPointsByChunk(
            List<QdrantMetadataReader.QdrantMetadataPoint> points,
            Set<String> issues) {
        Map<Long, List<QdrantMetadataReader.QdrantMetadataPoint>> grouped = new LinkedHashMap<>();
        for (QdrantMetadataReader.QdrantMetadataPoint point : points) {
            Long chunkId = toLong(point.metadata().get("chunkId"));
            if (chunkId == null) {
                issues.add("QDRANT_INVALID pointId=" + point.pointId() + " missing/invalid chunkId");
                continue;
            }
            grouped.computeIfAbsent(chunkId, ignored -> new ArrayList<>()).add(point);
        }
        return grouped;
    }

    private void checkDocumentMetadata(Document document,
                                       QdrantMetadataReader.QdrantMetadataPoint point,
                                       Set<String> issues) {
        String prefix = "pointId=" + point.pointId() + " ";
        compareRequired(issues, prefix + "documentId", document.getId(),
                toLong(point.metadata().get("documentId")));
        compareRequired(issues, prefix + "department", normalize(document.getDepartment()),
                stringValue(point.metadata().get("department")));
        compareRequired(issues, prefix + "year", document.getYear(),
                toInteger(point.metadata().get("year")));
        compareRequired(issues, prefix + "scope", normalizedScope(document.getScope()),
                stringValue(point.metadata().get("scope")));
        compareRequired(issues, prefix + "sourceType", normalize(document.getSourceType()),
                stringValue(point.metadata().get("sourceType")));
    }

    private void checkChunkMetadata(Document document,
                                    Chunk chunk,
                                    QdrantMetadataReader.QdrantMetadataPoint point,
                                    List<String> issues) {
        Map<String, Object> metadata = point.metadata();
        compareRequired(issues, "chunkId", chunk.getId(), toLong(metadata.get("chunkId")));
        compareOptional(issues, "chunkDepartment", normalize(chunk.getDepartment()),
                stringValue(metadata.get("chunkDepartment")));
        compareOptional(issues, "major", normalize(chunk.getMajor()),
                stringValue(metadata.get("major")));
        compareOptional(issues, "policyYear", chunk.getPolicyYear(),
                toInteger(metadata.get("policyYear")));
        compareOptional(issues, "cohortYear", chunk.getCohortYear(),
                toInteger(metadata.get("cohortYear")));
        Integer expectedEffectiveYear = chunk.getPolicyYear() != null
                ? chunk.getPolicyYear()
                : document.getYear();
        compareRequired(issues, "effectiveYear", expectedEffectiveYear,
                toInteger(metadata.get("effectiveYear")));
    }

    private void compareRequired(Set<String> issues, String field, Object expected, Object actual) {
        if (actual == null) {
            issues.add("QDRANT_MISSING " + field + " expected=" + expected);
        } else if (!Objects.equals(expected, actual)) {
            issues.add("MISMATCH " + field + " expected=" + expected + " actual=" + actual);
        }
    }

    private void compareRequired(List<String> issues, String field, Object expected, Object actual) {
        if (actual == null) {
            issues.add("QDRANT_MISSING " + field + " expected=" + expected);
        } else if (!Objects.equals(expected, actual)) {
            issues.add("MISMATCH " + field + " expected=" + expected + " actual=" + actual);
        }
    }

    private void compareOptional(List<String> issues, String field, Object expected, Object actual) {
        if (!Objects.equals(expected, actual)) {
            String type = actual == null ? "QDRANT_MISSING " : "MISMATCH ";
            issues.add(type + field + " expected=" + expected + " actual=" + actual);
        }
    }

    private Map<String, Object> mysqlDocumentSnapshot(Document document) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("documentId", document.getId());
        snapshot.put("department", document.getDepartment());
        snapshot.put("year", document.getYear());
        snapshot.put("scope", document.getScope());
        snapshot.put("sourceType", document.getSourceType());
        return snapshot;
    }

    private Map<String, Object> mysqlChunkSnapshot(Document document, Chunk chunk) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("chunkId", chunk.getId());
        snapshot.put("chunkIndex", chunk.getChunkIndex());
        snapshot.put("department", chunk.getDepartment());
        snapshot.put("major", chunk.getMajor());
        snapshot.put("policyYear", chunk.getPolicyYear());
        snapshot.put("cohortYear", chunk.getCohortYear());
        snapshot.put("effectiveYear", chunk.getPolicyYear() != null
                ? chunk.getPolicyYear()
                : document.getYear());
        return snapshot;
    }

    private Map<String, Object> qdrantSnapshot(
            QdrantMetadataReader.QdrantMetadataPoint point) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("pointId", point.pointId());
        snapshot.putAll(point.metadata());
        return snapshot;
    }

    private String normalizedScope(String scope) {
        String normalized = normalize(scope);
        return normalized == null
                ? Document.SCOPE_DEPARTMENT
                : normalized.toUpperCase(Locale.ROOT);
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String stringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private Long toLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String string && !string.isBlank()) {
            try {
                return Long.valueOf(string);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private Integer toInteger(Object value) {
        Long longValue = toLong(value);
        if (longValue == null || longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
            return null;
        }
        return longValue.intValue();
    }
}
