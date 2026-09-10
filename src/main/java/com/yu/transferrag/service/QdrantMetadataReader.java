package com.yu.transferrag.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class QdrantMetadataReader {

    private static final int PAGE_SIZE = 256;
    private static final Set<String> METADATA_FIELDS = Set.of(
            "documentId",
            "chunkId",
            "department",
            "chunkDepartment",
            "major",
            "year",
            "policyYear",
            "cohortYear",
            "effectiveYear",
            "scope",
            "sourceType"
    );

    private final RestClient restClient;
    private final String collectionName;

    @Autowired
    public QdrantMetadataReader(
            @Value("${spring.ai.vectorstore.qdrant.host}") String host,
            @Value("${spring.ai.vectorstore.qdrant.use-tls:false}") boolean useTls,
            @Value("${app.qdrant-rest-port:6333}") int restPort,
            @Value("${spring.ai.vectorstore.qdrant.collection-name}") String collectionName) {
        this(
                RestClient.builder()
                        .baseUrl((useTls ? "https" : "http") + "://" + host + ":" + restPort)
                        .build(),
                collectionName
        );
    }

    QdrantMetadataReader(RestClient restClient, String collectionName) {
        if (collectionName == null || !collectionName.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Qdrant collection-name 格式无效");
        }
        this.restClient = restClient;
        this.collectionName = collectionName;
    }

    public List<QdrantMetadataPoint> findByDocumentId(Long documentId) {
        List<QdrantMetadataPoint> points = new ArrayList<>();
        Object offset = null;

        do {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("limit", PAGE_SIZE);
            request.put("with_payload", true);
            request.put("with_vector", false);
            request.put("filter", Map.of(
                    "must", List.of(Map.of(
                            "key", "documentId",
                            "match", Map.of("value", documentId.toString())
                    ))
            ));
            if (offset != null) {
                request.put("offset", offset);
            }

            Map<String, Object> response = restClient.post()
                    .uri("/collections/{collection}/points/scroll", collectionName)
                    .body(request)
                    .retrieve()
                    .body(new ParameterizedTypeReference<>() {
                    });
            Map<String, Object> result = requiredMap(response, "result");
            for (Object rawPoint : requiredList(result, "points")) {
                Map<String, Object> point = castMap(rawPoint, "point");
                Map<String, Object> payload = castMap(point.get("payload"), "payload");
                Map<String, Object> selectedMetadata = new LinkedHashMap<>();
                for (String field : METADATA_FIELDS) {
                    if (payload.containsKey(field)) {
                        selectedMetadata.put(field, payload.get(field));
                    }
                }
                points.add(new QdrantMetadataPoint(
                        String.valueOf(point.get("id")),
                        Map.copyOf(selectedMetadata)
                ));
            }
            offset = result.get("next_page_offset");
        } while (offset != null);

        return List.copyOf(points);
    }

    private Map<String, Object> requiredMap(Map<String, Object> source, String key) {
        if (source == null || !source.containsKey(key)) {
            throw new IllegalStateException("Qdrant 响应缺少字段: " + key);
        }
        return castMap(source.get(key), key);
    }

    private List<?> requiredList(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof List<?> list) {
            return list;
        }
        throw new IllegalStateException("Qdrant 响应字段不是数组: " + key);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value, String fieldName) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalStateException("Qdrant 响应字段不是对象: " + fieldName);
    }

    public record QdrantMetadataPoint(String pointId, Map<String, Object> metadata) {
    }
}
