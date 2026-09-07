package com.yu.transferrag.service;

import com.yu.transferrag.dto.MatchedEntity;
import com.yu.transferrag.dto.QueryRewriteResult;
import com.yu.transferrag.dto.SearchResultResponse;
import com.yu.transferrag.repository.ChunkRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RetrievalService {

    private final VectorStore vectorStore;
    private final QueryRewriteService queryRewriteService;
    private final ChunkRepository chunkRepository;

    public RetrievalService(VectorStore vectorStore,
                            QueryRewriteService queryRewriteService,
                            ChunkRepository chunkRepository) {
        this.vectorStore = vectorStore;
        this.queryRewriteService = queryRewriteService;
        this.chunkRepository = chunkRepository;
    }

    public List<SearchResultResponse> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK 必须大于 0");
        }

        QueryRewriteResult rewriteResult = queryRewriteService.rewriteWithContext(query);
        Set<String> departments = extractDepartments(rewriteResult.matchedEntities());
        Integer resolvedYear = resolveYear(rewriteResult, departments);
        rewriteResult = rewriteResult.withResolvedYear(resolvedYear);

        if (departments.isEmpty()) {
            Filter.Expression metadataFilter = buildMetadataFilter(
                    departments,
                    rewriteResult.resolvedYear(),
                    rewriteResult.multiYearQuery(),
                    false
            );
            return executeSearch(rewriteResult.rewrittenQuery(), topK, metadataFilter);
        }

        Filter.Expression strictFilter = buildMetadataFilter(
                departments,
                rewriteResult.resolvedYear(),
                rewriteResult.multiYearQuery(),
                true
        );
        List<SearchResultResponse> strictResults = executeSearch(
                rewriteResult.rewrittenQuery(),
                topK,
                strictFilter
        );
        if (!strictResults.isEmpty()) {
            return strictResults;
        }

        Filter.Expression fallbackFilter = buildMetadataFilter(
                departments,
                rewriteResult.resolvedYear(),
                rewriteResult.multiYearQuery(),
                false
        );
        return executeSearch(
                rewriteResult.rewrittenQuery(),
                topK,
                fallbackFilter
        );
    }

    private List<SearchResultResponse> executeSearch(String rewrittenQuery,
                                                     int topK,
                                                     Filter.Expression metadataFilter) {
        SearchRequest.Builder requestBuilder = SearchRequest.builder()
                .query(rewrittenQuery)
                .topK(topK);
        if (metadataFilter != null) {
            requestBuilder.filterExpression(metadataFilter);
        }

        return vectorStore.similaritySearch(requestBuilder.build()).stream()
                .map(this::toResponse)
                .toList();
    }

    private Set<String> extractDepartments(List<MatchedEntity> matchedEntities) {
        Set<String> departments = new LinkedHashSet<>();
        for (MatchedEntity matchedEntity : matchedEntities) {
            String department = matchedEntity.department();
            if (department != null && !department.isBlank()) {
                departments.add(department.trim());
            }
        }
        return departments;
    }

    private Integer resolveYear(QueryRewriteResult rewriteResult,
                                Set<String> departments) {
        if (rewriteResult.multiYearQuery()) {
            return null;
        }
        if (rewriteResult.resolvedYear() != null) {
            return rewriteResult.resolvedYear();
        }
        if (departments.isEmpty()) {
            return chunkRepository.findMaxEffectiveYear();
        }
        return chunkRepository.findMaxEffectiveYearForDepartmentsOrGlobalOfficial(
                departments,
                com.yu.transferrag.entity.Document.SCOPE_GLOBAL,
                "OFFICIAL"
        );
    }

    private Filter.Expression buildMetadataFilter(Set<String> departments,
                                                   Integer resolvedYear,
                                                   boolean multiYearQuery,
                                                   boolean strictGlobalChunks) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op combinedFilter = buildDepartmentFilter(
                builder,
                departments,
                strictGlobalChunks
        );

        if (!multiYearQuery && resolvedYear != null) {
            FilterExpressionBuilder.Op yearFilter = builder.eq("effectiveYear", resolvedYear);
            combinedFilter = combinedFilter == null
                    ? yearFilter
                    : builder.and(combinedFilter, yearFilter);
        }

        return combinedFilter == null ? null : combinedFilter.build();
    }

    private FilterExpressionBuilder.Op buildDepartmentFilter(FilterExpressionBuilder builder,
                                                             Set<String> departments,
                                                             boolean strictGlobalChunks) {
        if (departments.isEmpty()) {
            return null;
        }

        FilterExpressionBuilder.Op targetDepartments = buildAnyDepartmentMatch(
                builder,
                "department",
                departments
        );

        FilterExpressionBuilder.Op globalOfficialDocuments = builder.and(
                builder.eq("scope", com.yu.transferrag.entity.Document.SCOPE_GLOBAL),
                builder.eq("sourceType", "OFFICIAL")
        );
        if (strictGlobalChunks) {
            FilterExpressionBuilder.Op targetChunkDepartments = buildAnyDepartmentMatch(
                    builder,
                    "chunkDepartment",
                    departments
            );
            globalOfficialDocuments = builder.and(
                    globalOfficialDocuments,
                    targetChunkDepartments
            );
        }
        return builder.or(targetDepartments, globalOfficialDocuments);
    }

    private FilterExpressionBuilder.Op buildAnyDepartmentMatch(FilterExpressionBuilder builder,
                                                               String metadataField,
                                                               Set<String> departments) {
        FilterExpressionBuilder.Op matches = null;
        for (String department : departments) {
            FilterExpressionBuilder.Op current = builder.eq(metadataField, department);
            matches = matches == null ? current : builder.or(matches, current);
        }
        return matches;
    }

    private SearchResultResponse toResponse(Document document) {
        Map<String, Object> metadata = document.getMetadata();

        SearchResultResponse response = new SearchResultResponse();
        response.setContent(document.getText());
        response.setScore(document.getScore());
        response.setChunkId(toLong(metadata.get("chunkId"), "chunkId"));
        response.setDocumentId(toLong(metadata.get("documentId"), "documentId"));
        response.setChunkIndex(toInteger(metadata.get("chunkIndex"), "chunkIndex"));
        response.setPolicyYear(toInteger(metadata.get("policyYear"), "policyYear"));
        response.setEffectiveYear(toInteger(metadata.get("effectiveYear"), "effectiveYear"));
        response.setChunkDepartment(toStringValue(metadata.get("chunkDepartment")));
        response.setMajor(toStringValue(metadata.get("major")));
        return response;
    }

    private String toStringValue(Object value) {
        return value == null ? null : value.toString();
    }

    private Long toLong(Object value, String fieldName) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value instanceof String stringValue) {
            try {
                return Long.valueOf(stringValue);
            } catch (NumberFormatException e) {
                throw invalidMetadata(fieldName, value, e);
            }
        }
        throw invalidMetadata(fieldName, value, null);
    }

    private Integer toInteger(Object value, String fieldName) {
        Long longValue = toLong(value, fieldName);
        if (longValue == null) {
            return null;
        }
        if (longValue < Integer.MIN_VALUE || longValue > Integer.MAX_VALUE) {
            throw invalidMetadata(fieldName, value, null);
        }
        return longValue.intValue();
    }

    private IllegalStateException invalidMetadata(String fieldName,
                                                  Object value,
                                                  Exception cause) {
        return new IllegalStateException(
                "无法将 metadata." + fieldName + " 转换为整数: " + value,
                cause
        );
    }
}
