package com.yu.transferrag.service;

import io.qdrant.client.ConditionFactory;
import io.qdrant.client.QdrantClient;
import io.qdrant.client.WithPayloadSelectorFactory;
import io.qdrant.client.grpc.Common.Condition;
import io.qdrant.client.grpc.Common.Filter;
import io.qdrant.client.grpc.JsonWithInt.Value;
import io.qdrant.client.grpc.Points.ScoredPoint;
import io.qdrant.client.grpc.Points.SearchPoints;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.model.EmbeddingUtils;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
public class QdrantPrecomputedVectorSearch implements PrecomputedVectorSearch {

    private static final String CONTENT_FIELD = "doc_content";

    private final VectorStore vectorStore;
    private final EmbeddingModel embeddingModel;
    private final String collectionName;

    public QdrantPrecomputedVectorSearch(
            VectorStore vectorStore,
            EmbeddingModel embeddingModel,
            @org.springframework.beans.factory.annotation.Value(
                    "${spring.ai.vectorstore.qdrant.collection-name}"
            ) String collectionName) {
        this.vectorStore = vectorStore;
        this.embeddingModel = embeddingModel;
        this.collectionName = collectionName;
    }

    @Override
    public float[] embed(String query) {
        return embeddingModel.embed(query);
    }

    @Override
    public List<VectorMatch> search(float[] queryEmbedding,
                                    int topK,
                                    org.springframework.ai.vectorstore.filter.Filter.Expression filter) {
        QdrantClient client = vectorStore.<QdrantClient>getNativeClient()
                .orElseThrow(() -> new IllegalStateException("VectorStore 未提供 QdrantClient"));
        SearchPoints.Builder request = SearchPoints.newBuilder()
                .setCollectionName(collectionName)
                .setLimit(topK)
                .setWithPayload(WithPayloadSelectorFactory.enable(true))
                .addAllVector(EmbeddingUtils.toList(queryEmbedding));
        if (filter != null) {
            request.setFilter(toQdrantFilter(filter));
        }

        try {
            return client.searchAsync(request.build()).get().stream()
                    .map(this::toMatch)
                    .toList();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Qdrant search interrupted", exception);
        } catch (ExecutionException exception) {
            throw new IllegalStateException("Qdrant search failed", exception.getCause());
        }
    }

    Filter toQdrantFilter(org.springframework.ai.vectorstore.filter.Filter.Operand operand) {
        Filter.Builder result = Filter.newBuilder();
        if (!(operand instanceof org.springframework.ai.vectorstore.filter.Filter.Expression expression)) {
            throw new IllegalArgumentException("不支持的 Qdrant filter operand");
        }
        return switch (expression.type()) {
            case AND -> result
                    .addMust(ConditionFactory.filter(toQdrantFilter(expression.left())))
                    .addMust(ConditionFactory.filter(toQdrantFilter(expression.right())))
                    .build();
            case OR -> result
                    .addShould(ConditionFactory.filter(toQdrantFilter(expression.left())))
                    .addShould(ConditionFactory.filter(toQdrantFilter(expression.right())))
                    .build();
            case EQ -> result.addMust(equalityCondition(expression)).build();
            case NE -> result.addMustNot(equalityCondition(expression)).build();
            default -> throw new IllegalArgumentException(
                    "当前预计算向量搜索不支持 filter: " + expression.type()
            );
        };
    }

    private Condition equalityCondition(org.springframework.ai.vectorstore.filter.Filter.Expression expression) {
        if (!(expression.left() instanceof org.springframework.ai.vectorstore.filter.Filter.Key key)
                || !(expression.right() instanceof org.springframework.ai.vectorstore.filter.Filter.Value value)) {
            throw new IllegalArgumentException("EQ/NE filter 格式无效");
        }
        Object rawValue = value.value();
        if (rawValue instanceof String stringValue) {
            return ConditionFactory.matchKeyword(key.key(), stringValue);
        }
        if (rawValue instanceof Number numberValue) {
            return ConditionFactory.match(key.key(), numberValue.longValue());
        }
        throw new IllegalArgumentException("EQ/NE filter 仅支持字符串或数字");
    }

    private VectorMatch toMatch(ScoredPoint point) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        point.getPayloadMap().forEach((key, value) -> metadata.put(key, toObject(value)));
        String content = metadata.remove(CONTENT_FIELD) instanceof String value ? value : "";
        return new VectorMatch(content, Collections.unmodifiableMap(metadata), (double) point.getScore());
    }

    private Object toObject(Value value) {
        return switch (value.getKindCase()) {
            case NULL_VALUE, KIND_NOT_SET -> null;
            case DOUBLE_VALUE -> value.getDoubleValue();
            case INTEGER_VALUE -> value.getIntegerValue();
            case STRING_VALUE -> value.getStringValue();
            case BOOL_VALUE -> value.getBoolValue();
            case STRUCT_VALUE -> {
                Map<String, Object> nested = new LinkedHashMap<>();
                value.getStructValue().getFieldsMap()
                        .forEach((key, child) -> nested.put(key, toObject(child)));
                yield nested;
            }
            case LIST_VALUE -> {
                List<Object> values = new ArrayList<>();
                value.getListValue().getValuesList().forEach(child -> values.add(toObject(child)));
                yield values;
            }
        };
    }
}
