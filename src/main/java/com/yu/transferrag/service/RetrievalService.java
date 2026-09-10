package com.yu.transferrag.service;

import com.yu.transferrag.dto.QueryRewriteResult;
import com.yu.transferrag.dto.SearchResultResponse;
import com.yu.transferrag.repository.ChunkRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RetrievalService {

    private static final int EXPERIENCE_CANDIDATE_MULTIPLIER = 3;
    private static final int MIN_EXPERIENCE_CANDIDATES = 10;
    private static final double LATEST_YEAR_BONUS = 0.03;
    private static final double PREVIOUS_YEAR_BONUS = 0.02;
    private static final double TWO_YEARS_AGO_BONUS = 0.01;

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
        Set<String> departments = new LinkedHashSet<>(rewriteResult.departments());
        boolean historicalCohortQuery = isHistoricalCohortQuery(rewriteResult);
        boolean crossYearExperienceQuery = shouldSearchExperienceAcrossYears(rewriteResult);
        Integer resolvedYear = historicalCohortQuery
                ? null
                : resolveYear(rewriteResult, departments);
        rewriteResult = rewriteResult.withResolvedYear(resolvedYear);
        Integer filterYear = crossYearExperienceQuery ? null : rewriteResult.resolvedYear();
        int searchTopK = crossYearExperienceQuery ? experienceCandidateTopK(topK) : topK;

        if (departments.isEmpty()) {
            Filter.Expression metadataFilter = buildMetadataFilter(
                    departments,
                    filterYear,
                    rewriteResult.multiYearQuery(),
                    false,
                    rewriteResult
            );
            List<SearchResultResponse> results = executeSearch(
                    rewriteResult.rewrittenQuery(),
                    searchTopK,
                    metadataFilter
            );
            if (historicalCohortQuery && results.isEmpty()) {
                return searchUntaggedHistoricalFallback(
                        rewriteResult.rewrittenQuery(), departments, topK
                );
            }
            return finalizeResults(results, topK, resolvedYear, crossYearExperienceQuery);
        }

        Filter.Expression strictFilter = buildMetadataFilter(
                departments,
                filterYear,
                rewriteResult.multiYearQuery(),
                true,
                rewriteResult
        );
        List<SearchResultResponse> strictResults = executeSearch(
                rewriteResult.rewrittenQuery(),
                searchTopK,
                strictFilter
        );
        if (!strictResults.isEmpty()) {
            return finalizeResults(
                    strictResults,
                    topK,
                    resolvedYear,
                    crossYearExperienceQuery
            );
        }

        Filter.Expression fallbackFilter = buildMetadataFilter(
                departments,
                filterYear,
                rewriteResult.multiYearQuery(),
                false,
                rewriteResult
        );
        List<SearchResultResponse> fallbackResults = executeSearch(
                rewriteResult.rewrittenQuery(),
                searchTopK,
                fallbackFilter
        );
        if (historicalCohortQuery && fallbackResults.isEmpty()) {
            return searchUntaggedHistoricalFallback(
                    rewriteResult.rewrittenQuery(), departments, topK
            );
        }
        return finalizeResults(
                fallbackResults,
                topK,
                resolvedYear,
                crossYearExperienceQuery
        );
    }

    private boolean isHistoricalCohortQuery(QueryRewriteResult rewriteResult) {
        return rewriteResult.cohortYear() != null && rewriteResult.cycleYear() == null;
    }

    private List<SearchResultResponse> searchUntaggedHistoricalFallback(
            String rewrittenQuery, Set<String> departments, int topK) {
        if (departments.isEmpty()) {
            return untagged(executeSearch(rewrittenQuery, topK, null));
        }

        List<SearchResultResponse> strict = untagged(executeSearch(
                rewrittenQuery,
                topK,
                buildDepartmentOnlyFilter(departments, true)
        ));
        if (!strict.isEmpty()) {
            return strict;
        }
        return untagged(executeSearch(
                rewrittenQuery,
                topK,
                buildDepartmentOnlyFilter(departments, false)
        ));
    }

    private List<SearchResultResponse> untagged(List<SearchResultResponse> results) {
        return results.stream()
                .filter(result -> result.getCohortYear() == null)
                .toList();
    }

    private Filter.Expression buildDepartmentOnlyFilter(Set<String> departments,
                                                        boolean strictGlobalChunks) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op filter = buildDepartmentFilter(
                builder, departments, strictGlobalChunks
        );
        return filter == null ? null : filter.build();
    }

    private boolean shouldSearchExperienceAcrossYears(QueryRewriteResult rewriteResult) {
        return rewriteResult.experienceQuery()
                && rewriteResult.explicitYear() == null
                && rewriteResult.resolvedYear() == null
                && !rewriteResult.multiYearQuery();
    }

    private int experienceCandidateTopK(int topK) {
        int multipliedTopK = topK > Integer.MAX_VALUE / EXPERIENCE_CANDIDATE_MULTIPLIER
                ? Integer.MAX_VALUE
                : topK * EXPERIENCE_CANDIDATE_MULTIPLIER;
        return Math.max(multipliedTopK, MIN_EXPERIENCE_CANDIDATES);
    }

    private List<SearchResultResponse> finalizeResults(List<SearchResultResponse> results,
                                                       int topK,
                                                       Integer latestYear,
                                                       boolean crossYearExperienceQuery) {
        if (!crossYearExperienceQuery) {
            return results;
        }

        Comparator<SearchResultResponse> ranking = Comparator
                .comparingDouble((SearchResultResponse result) -> adjustedScore(result, latestYear))
                .reversed()
                .thenComparing(
                        SearchResultResponse::getEffectiveYear,
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
                .thenComparing(
                        SearchResultResponse::getScore,
                        Comparator.nullsLast(Comparator.reverseOrder())
                );

        return results.stream()
                .sorted(ranking)
                .limit(topK)
                .toList();
    }

    private double adjustedScore(SearchResultResponse result, Integer latestYear) {
        double vectorScore = result.getScore() == null
                ? -Double.MAX_VALUE
                : result.getScore();
        return vectorScore + yearBonus(result.getEffectiveYear(), latestYear);
    }

    private double yearBonus(Integer effectiveYear, Integer latestYear) {
        if (effectiveYear == null || latestYear == null) {
            return 0.0;
        }

        int yearDifference = latestYear - effectiveYear;
        return switch (yearDifference) {
            case 0 -> LATEST_YEAR_BONUS;
            case 1 -> PREVIOUS_YEAR_BONUS;
            case 2 -> TWO_YEARS_AGO_BONUS;
            default -> 0.0;
        };
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
                                                   boolean strictGlobalChunks,
                                                   QueryRewriteResult rewriteResult) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op combinedFilter = buildDepartmentFilter(
                builder,
                departments,
                strictGlobalChunks
        );

        FilterExpressionBuilder.Op yearFilter = null;
        if (rewriteResult.policyQuery() && rewriteResult.cycleYear() != null) {
            yearFilter = builder.eq("policyYear", rewriteResult.cycleYear());
            if (rewriteResult.cohortYear() != null) {
                yearFilter = builder.and(
                        yearFilter,
                        builder.eq("cohortYear", rewriteResult.cohortYear())
                );
            }
        } else if (isHistoricalCohortQuery(rewriteResult)) {
            yearFilter = builder.eq("cohortYear", rewriteResult.cohortYear());
        } else if (!multiYearQuery && resolvedYear != null) {
            yearFilter = builder.eq("effectiveYear", resolvedYear);
        }
        if (yearFilter != null) {
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
        response.setCohortYear(toInteger(metadata.get("cohortYear"), "cohortYear"));
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
