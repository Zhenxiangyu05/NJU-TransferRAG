package com.yu.transferrag.service;

import com.yu.transferrag.dto.QdrantPayloadBackfillReport;
import com.yu.transferrag.entity.Chunk;
import com.yu.transferrag.entity.DocumentRole;
import com.yu.transferrag.repository.ChunkRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class QdrantPayloadBackfillService {

    private static final Logger logger = LoggerFactory.getLogger(QdrantPayloadBackfillService.class);
    private static final int MAX_REPORTED_WARNINGS = 100;

    private final ChunkRepository chunkRepository;
    private final QdrantPayloadGateway qdrantPayloadGateway;

    public QdrantPayloadBackfillService(ChunkRepository chunkRepository,
                                        QdrantPayloadGateway qdrantPayloadGateway) {
        this.chunkRepository = chunkRepository;
        this.qdrantPayloadGateway = qdrantPayloadGateway;
    }

    /**
     * Updates only Qdrant payload fields. It never changes vectors, deletes points,
     * calls the embedding model, or recreates the collection.
     */
    @Transactional(readOnly = true)
    public QdrantPayloadBackfillReport backfill(boolean dryRun) {
        List<QdrantPayloadGateway.QdrantPoint> points = qdrantPayloadGateway.scrollAllPoints();
        Set<Long> chunkIds = points.stream()
                .map(QdrantPayloadGateway.QdrantPoint::payload)
                .map(payload -> positiveLong(payload.get("chunkId")))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Chunk> chunksById = chunkRepository.findAllByIdIn(chunkIds).stream()
                .collect(Collectors.toMap(Chunk::getId, chunk -> chunk));

        int alreadyCorrect = 0;
        int missingChunks = 0;
        int canonicalPreserved = 0;
        int errors = 0;
        List<String> warnings = new ArrayList<>();
        Map<Map<String, Object>, List<Object>> updateGroups = new LinkedHashMap<>();

        for (QdrantPayloadGateway.QdrantPoint point : points) {
            try {
                Long chunkId = positiveLong(point.payload().get("chunkId"));
                Chunk chunk = chunkId == null ? null : chunksById.get(chunkId);
                if (chunk == null || chunk.getDocument() == null) {
                    missingChunks++;
                    Long documentId = positiveLong(point.payload().get("documentId"));
                    addWarning(warnings, "missing chunk/document for pointId=" + point.id()
                            + (documentId == null ? "" : " documentId=" + documentId));
                    continue;
                }

                DocumentRole desiredRole = chunk.getDocument().getDocumentRole();
                String currentRole = normalizedString(point.payload().get("documentRole"));
                if (DocumentRole.CANONICAL.name().equals(currentRole)
                        && desiredRole == DocumentRole.EVIDENCE) {
                    canonicalPreserved++;
                    addWarning(warnings, "preserved CANONICAL pointId=" + point.id()
                            + " because MySQL chunk " + chunkId + " resolves to EVIDENCE");
                    continue;
                }

                Map<String, Object> payloadUpdate = new LinkedHashMap<>();
                if (!desiredRole.name().equals(currentRole)) {
                    payloadUpdate.put("documentRole", desiredRole.name());
                }
                String section = trimToNull(chunk.getSection());
                if (section != null && !section.equals(point.payload().get("section"))) {
                    payloadUpdate.put("section", section);
                }
                if (payloadUpdate.isEmpty()) {
                    alreadyCorrect++;
                } else {
                    updateGroups.computeIfAbsent(Map.copyOf(payloadUpdate), ignored -> new ArrayList<>())
                            .add(point.id());
                }
            } catch (RuntimeException exception) {
                errors++;
                addWarning(warnings, "failed to inspect pointId=" + point.id());
                logger.warn("Qdrant payload backfill inspection failed: pointId={}, exceptionType={}",
                        point.id(), exception.getClass().getName());
            }
        }

        int requiresUpdate = updateGroups.values().stream().mapToInt(Collection::size).sum();
        int updatedPoints = 0;
        if (!dryRun) {
            for (Map.Entry<Map<String, Object>, List<Object>> update : updateGroups.entrySet()) {
                try {
                    qdrantPayloadGateway.setPayload(update.getValue(), update.getKey());
                    updatedPoints += update.getValue().size();
                } catch (RuntimeException exception) {
                    errors += update.getValue().size();
                    addWarning(warnings, "failed to update " + update.getValue().size() + " point payloads");
                    logger.warn("Qdrant payload backfill update failed: pointCount={}, exceptionType={}",
                            update.getValue().size(), exception.getClass().getName());
                }
            }
        }
        return new QdrantPayloadBackfillReport(
                dryRun, points.size(), requiresUpdate, updatedPoints, alreadyCorrect,
                missingChunks, canonicalPreserved, errors, List.copyOf(warnings)
        );
    }

    private Long positiveLong(Object value) {
        if (value instanceof Number number) {
            long result = number.longValue();
            return result > 0 ? result : null;
        }
        if (value instanceof String stringValue) {
            try {
                long result = Long.parseLong(stringValue.trim());
                return result > 0 ? result : null;
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    private String normalizedString(Object value) {
        String trimmed = trimToNull(value == null ? null : value.toString());
        return trimmed == null ? null : trimmed.toUpperCase(Locale.ROOT);
    }

    private String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private void addWarning(List<String> warnings, String warning) {
        if (warnings.size() < MAX_REPORTED_WARNINGS) {
            warnings.add(warning);
        }
    }
}
