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
            ScrollPage page = parseScrollPage(response);
            points.addAll(page.points());
            offset = page.nextPageOffset();
        } while (offset != null);
        return List.copyOf(points);
    }

    /**
     * Qdrant scroll responses wrap both the point array and cursor in {@code result}.
     * Keep this decoding in one place so pagination cannot accidentally read fields
     * from the top-level transport envelope.
     */
    private ScrollPage parseScrollPage(Map<String, Object> response) {
        Map<String, Object> result = requiredObjectField(response, "result");
        List<QdrantPoint> points = new ArrayList<>();
        for (Object rawPoint : requiredList(result, "points")) {
            Map<String, Object> point = requiredMapValue(rawPoint, "point");
            Object pointId = point.get("id");
            if (pointId == null) {
                throw new IllegalStateException("Qdrant point 缺少 id");
            }
            points.add(new QdrantPoint(pointId, requiredMapValue(point.get("payload"), "payload")));
        }
        return new ScrollPage(List.copyOf(points), result.get("next_page_offset"));
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

    private Map<String, Object> requiredObjectField(Map<String, Object> source, String field) {
        if (source == null || !source.containsKey(field)) {
            throw new IllegalStateException("Qdrant 响应缺少对象字段: " + field);
        }
        return requiredMapValue(source.get(field), field);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> requiredMapValue(Object value, String field) {
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
        throw new IllegalStateException("Qdrant 响应缺少数组字段: " + key
                + "，当前对象字段: " + source.keySet());
    }

    private record ScrollPage(List<QdrantPoint> points, Object nextPageOffset) {
    }
}
