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

@Service
public class QdrantRestPayloadGateway implements QdrantPayloadGateway {

    private static final int PAGE_SIZE = 256;

    private final RestClient restClient;
    private final String collectionName;

    @Autowired
    public QdrantRestPayloadGateway(
            @Value("${spring.ai.vectorstore.qdrant.host}") String host,
            @Value("${spring.ai.vectorstore.qdrant.use-tls:false}") boolean useTls,
            @Value("${app.qdrant-rest-port:6333}") int restPort,
            @Value("${spring.ai.vectorstore.qdrant.collection-name}") String collectionName) {
        this(RestClient.builder()
                        .baseUrl((useTls ? "https" : "http") + "://" + host + ":" + restPort)
                        .build(),
                collectionName);
    }

    QdrantRestPayloadGateway(RestClient restClient, String collectionName) {
        if (collectionName == null || !collectionName.matches("[A-Za-z0-9_-]+")) {
            throw new IllegalArgumentException("Qdrant collection-name 格式无效");
        }
        this.restClient = restClient;
        this.collectionName = collectionName;
    }

    @Override
    public List<QdrantPoint> scrollAllPoints() {
        List<QdrantPoint> points = new ArrayList<>();
        Object offset = null;
        do {
            Map<String, Object> request = new LinkedHashMap<>();
            request.put("limit", PAGE_SIZE);
            request.put("with_payload", true);
            request.put("with_vector", false);
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
                Map<String, Object> point = requiredMap(rawPoint, "point");
                Object pointId = point.get("id");
                if (pointId == null) {
                    throw new IllegalStateException("Qdrant point 缺少 id");
                }
                points.add(new QdrantPoint(pointId, requiredMap(point.get("payload"), "payload")));
            }
            offset = result.get("next_page_offset");
        } while (offset != null);
        return List.copyOf(points);
    }

    @Override
    public void setPayload(List<Object> pointIds, Map<String, Object> payload) {
        if (pointIds.isEmpty() || payload.isEmpty()) {
            return;
        }
        restClient.post()
                .uri("/collections/{collection}/points/payload", collectionName)
                .body(Map.of("points", pointIds, "payload", payload))
                .retrieve()
                .toBodilessEntity();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requiredMap(Object value, String field) {
        if (value instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalStateException("Qdrant 响应缺少对象字段: " + field);
    }

    private List<?> requiredList(Map<String, Object> source, String key) {
        Object value = source.get(key);
        if (value instanceof List<?> list) {
            return list;
        }
        throw new IllegalStateException("Qdrant 响应缺少数组字段: " + key);
    }
}
