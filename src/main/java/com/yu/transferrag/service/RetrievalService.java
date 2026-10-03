package com.yu.transferrag.service;

import com.yu.transferrag.exception.AiServiceUnavailableException;

import com.yu.transferrag.dto.QueryRewriteResult;
import com.yu.transferrag.dto.SearchResultResponse;
import com.yu.transferrag.entity.DocumentRole;
import com.yu.transferrag.repository.ChunkRepository;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.Collections;
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
    private static final int EVIDENCE_CANDIDATE_MULTIPLIER = 4;
    private static final int MIN_EVIDENCE_CANDIDATES = 20;

    private final VectorStore vectorStore;
    private final QueryRewriteService queryRewriteService;
    private final ChunkRepository chunkRepository;
    private final PrecomputedVectorSearch precomputedVectorSearch;

    public RetrievalService(VectorStore vectorStore,
                            QueryRewriteService queryRewriteService,
                            ChunkRepository chunkRepository,
                            PrecomputedVectorSearch precomputedVectorSearch) {
        this.vectorStore = vectorStore;
        this.queryRewriteService = queryRewriteService;
        this.chunkRepository = chunkRepository;
        this.precomputedVectorSearch = precomputedVectorSearch;
    }

    public List<SearchResultResponse> search(String query, int topK) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK 必须大于 0");
        }

        RetrievalPlan plan = createPlan(query);
        return executePlan(plan, topK, (candidateTopK, filter) -> executeSearch(
                plan.rewriteResult().rewrittenQuery(), candidateTopK, filter
        ), DocumentRole.EVIDENCE);
    }

    public PreparedQuery prepareCanonicalFirst(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }
        RetrievalPlan plan = createPlan(query);
        float[] embedding = precomputedVectorSearch.embed(plan.rewriteResult().rewrittenQuery());
        return new PreparedQuery(plan, embedding);
    }

    public List<SearchResultResponse> searchCanonical(PreparedQuery preparedQuery, int topK) {
        validatePrepared(preparedQuery, topK);
        return executePreparedPlan(preparedQuery, topK, DocumentRole.CANONICAL);
    }

    public List<SearchResultResponse> searchEvidence(PreparedQuery preparedQuery, int topK) {
        validatePrepared(preparedQuery, topK);
        return executePreparedPlan(preparedQuery, topK, DocumentRole.EVIDENCE);
    }

    private List<SearchResultResponse> executePreparedPlan(PreparedQuery preparedQuery,
                                                            int topK,
                                                            DocumentRole role) {
        RetrievalPlan plan = preparedQuery.plan;
        QueryRewriteResult rewrite = plan.rewriteResult();
        Integer filterYear = plan.crossYearExperienceQuery() ? null : rewrite.resolvedYear();
        Filter.Expression filter = plan.historicalCohortQuery()
                ? buildDepartmentOnlyFilter(plan.departments(), false, role)
                : buildMetadataFilter(
                        plan.departments(), filterYear, rewrite.multiYearQuery(),
                        false, rewrite, role
                );
        int candidateTopK = preparedCandidateTopK(topK, plan.crossYearExperienceQuery());
        List<SearchResultResponse> candidates = executePrecomputed(
                preparedQuery.embedding, candidateTopK, filter, role
        );

        if (plan.historicalCohortQuery()) {
            List<SearchResultResponse> exactCohort = candidates.stream()
                    .filter(result -> rewrite.cohortYear().equals(result.getCohortYear()))
                    .toList();
            candidates = exactCohort.isEmpty() ? untagged(candidates) : exactCohort;
        }

        if (!plan.departments().isEmpty()) {
            List<SearchResultResponse> strict = candidates.stream()
                    .filter(result -> isStrictDepartmentMatch(result, plan.departments()))
                    .toList();
            if (!strict.isEmpty()) {
                candidates = strict;
            }
        }
        return finalizeResults(candidates, topK, plan.resolvedYear(),
                plan.crossYearExperienceQuery()).stream().limit(topK).toList();
    }

    private int preparedCandidateTopK(int topK, boolean crossYearExperienceQuery) {
        if (crossYearExperienceQuery) {
            return experienceCandidateTopK(topK);
        }
        int multiplied = topK > Integer.MAX_VALUE / EVIDENCE_CANDIDATE_MULTIPLIER
                ? Integer.MAX_VALUE
                : topK * EVIDENCE_CANDIDATE_MULTIPLIER;
        return Math.max(MIN_EVIDENCE_CANDIDATES, multiplied);
    }

    private boolean isStrictDepartmentMatch(SearchResultResponse result,
                                            Set<String> departments) {
        if (departments.contains(result.getDocumentDepartment())) {
            return true;
        }
        return com.yu.transferrag.entity.Document.SCOPE_GLOBAL.equals(result.getScope())
                && departments.contains(result.getChunkDepartment());
    }

    private RetrievalPlan createPlan(String query) {
        QueryRewriteResult rewriteResult = queryRewriteService.rewriteWithContext(query);
        Set<String> departments = new LinkedHashSet<>(rewriteResult.departments());
        boolean historicalCohortQuery = isHistoricalCohortQuery(rewriteResult);
        boolean crossYearExperienceQuery = shouldSearchExperienceAcrossYears(rewriteResult);
        Integer resolvedYear = historicalCohortQuery ? null : resolveYear(rewriteResult, departments);
        return new RetrievalPlan(
                rewriteResult.withResolvedYear(resolvedYear),
                Collections.unmodifiableSet(departments),
                historicalCohortQuery,
                crossYearExperienceQuery,
                resolvedYear
        );
    }

    private List<SearchResultResponse> executePlan(RetrievalPlan plan,
                                                    int topK,
                                                    SearchExecutor searchExecutor,
                                                    DocumentRole role) {
        QueryRewriteResult rewriteResult = plan.rewriteResult();
        Set<String> departments = plan.departments();
        boolean historicalCohortQuery = plan.historicalCohortQuery();
        boolean crossYearExperienceQuery = plan.crossYearExperienceQuery();
        Integer resolvedYear = plan.resolvedYear();
        Integer filterYear = crossYearExperienceQuery ? null : rewriteResult.resolvedYear();
        int searchTopK = crossYearExperienceQuery ? experienceCandidateTopK(topK) : topK;

        if (departments.isEmpty()) {
            Filter.Expression metadataFilter = buildMetadataFilter(
                    departments,
                    filterYear,
                    rewriteResult.multiYearQuery(),
                    false,
                    rewriteResult,
                    role
            );
            List<SearchResultResponse> results = searchExecutor.search(searchTopK, metadataFilter);
            if (historicalCohortQuery && results.isEmpty()) {
                return searchUntaggedHistoricalFallback(departments, topK, role, searchExecutor);
            }
            return finalizeResults(results, topK, resolvedYear, crossYearExperienceQuery);
        }

        Filter.Expression strictFilter = buildMetadataFilter(
                departments,
                filterYear,
                rewriteResult.multiYearQuery(),
                true,
                rewriteResult,
                role
        );
        List<SearchResultResponse> strictResults = searchExecutor.search(searchTopK, strictFilter);
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
                rewriteResult,
                role
        );
        List<SearchResultResponse> fallbackResults = searchExecutor.search(searchTopK, fallbackFilter);
        if (historicalCohortQuery && fallbackResults.isEmpty()) {
            return searchUntaggedHistoricalFallback(departments, topK, role, searchExecutor);
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
            Set<String> departments,
            int topK,
            DocumentRole role,
            SearchExecutor searchExecutor) {
        if (departments.isEmpty()) {
            return untagged(searchExecutor.search(topK, withDocumentRole(null, role)));
        }

        List<SearchResultResponse> strict = untagged(searchExecutor.search(
                topK,
                buildDepartmentOnlyFilter(departments, true, role)
        ));
        if (!strict.isEmpty()) {
            return strict;
        }
        return untagged(searchExecutor.search(
                topK,
                buildDepartmentOnlyFilter(departments, false, role)
        ));
    }

    private List<SearchResultResponse> untagged(List<SearchResultResponse> results) {
        return results.stream()
                .filter(result -> result.getCohortYear() == null)
                .toList();
    }

    private Filter.Expression buildDepartmentOnlyFilter(Set<String> departments,
                                                        boolean strictGlobalChunks,
                                                        DocumentRole role) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op filter = buildDepartmentFilter(
                builder, departments, strictGlobalChunks, role
        );
        Filter.Expression result = filter == null ? null : filter.build();
        return withDocumentRole(result, role);
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

        return AiServiceUnavailableException.call(AiServiceUnavailableException.Stage.EMBEDDING,
                () -> vectorStore.similaritySearch(requestBuilder.build())).stream()
                .map(this::toResponse)
                .filter(result -> result.getDocumentRole() == DocumentRole.EVIDENCE)
                .toList();
    }

    private List<SearchResultResponse> executePrecomputed(float[] embedding,
                                                          int topK,
                                                          Filter.Expression metadataFilter,
                                                          DocumentRole expectedRole) {
        return precomputedVectorSearch.search(embedding, topK, metadataFilter).stream()
                .map(this::toResponse)
                .filter(result -> result.getDocumentRole() == expectedRole)
                .limit(topK)
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
                                                   QueryRewriteResult rewriteResult,
                                                   DocumentRole role) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op combinedFilter = buildDepartmentFilter(
                builder,
                departments,
                strictGlobalChunks,
                role
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

        Filter.Expression result = combinedFilter == null ? null : combinedFilter.build();
        return withDocumentRole(result, role);
    }

    private FilterExpressionBuilder.Op buildDepartmentFilter(FilterExpressionBuilder builder,
                                                             Set<String> departments,
                                                             boolean strictGlobalChunks,
                                                             DocumentRole role) {
        if (departments.isEmpty()) {
            return null;
        }

        FilterExpressionBuilder.Op targetDepartments = buildAnyDepartmentMatch(
                builder,
                "department",
                departments
        );

        FilterExpressionBuilder.Op globalOfficialDocuments = role == DocumentRole.CANONICAL
                ? builder.and(
                        builder.eq("scope", com.yu.transferrag.entity.Document.SCOPE_GLOBAL),
                        builder.eq("documentRole", DocumentRole.CANONICAL.name())
                )
                : builder.and(
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
        applyV2Metadata(response, metadata);
        return response;
    }

    private SearchResultResponse toResponse(PrecomputedVectorSearch.VectorMatch match) {
        SearchResultResponse response = new SearchResultResponse();
        Map<String, Object> metadata = match.metadata();
        response.setContent(match.content());
        response.setScore(match.score());
        response.setChunkId(toLong(metadata.get("chunkId"), "chunkId"));
        response.setDocumentId(toLong(metadata.get("documentId"), "documentId"));
        response.setChunkIndex(toInteger(metadata.get("chunkIndex"), "chunkIndex"));
        response.setPolicyYear(toInteger(metadata.get("policyYear"), "policyYear"));
        response.setCohortYear(toInteger(metadata.get("cohortYear"), "cohortYear"));
        response.setEffectiveYear(toInteger(metadata.get("effectiveYear"), "effectiveYear"));
        response.setChunkDepartment(toStringValue(metadata.get("chunkDepartment")));
        response.setMajor(toStringValue(metadata.get("major")));
        applyV2Metadata(response, metadata);
        return response;
    }

    private void applyV2Metadata(SearchResultResponse response, Map<String, Object> metadata) {
        String roleValue = toStringValue(metadata.get("documentRole"));
        DocumentRole role;
        try {
            role = roleValue == null ? DocumentRole.EVIDENCE : DocumentRole.valueOf(roleValue);
        } catch (IllegalArgumentException exception) {
            role = DocumentRole.EVIDENCE;
        }
        response.setDocumentRole(role);
        response.setSection(toStringValue(metadata.get("section")));
        response.setRetrievalLayer(role.name());
        response.setDocumentDepartment(toStringValue(metadata.get("department")));
        response.setScope(toStringValue(metadata.get("scope")));
        response.setSourceType(toStringValue(metadata.get("sourceType")));
    }

    private Filter.Expression withDocumentRole(Filter.Expression filter, DocumentRole role) {
        if (role == null) {
            return filter;
        }
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op roleFilter = role == DocumentRole.CANONICAL
                ? builder.eq("documentRole", DocumentRole.CANONICAL.name())
                : builder.ne("documentRole", DocumentRole.CANONICAL.name());
        return filter == null
                ? roleFilter.build()
                : builder.and(roleFilter, new FilterExpressionBuilder.Op(filter)).build();
    }

    private void validatePrepared(PreparedQuery preparedQuery, int topK) {
        if (preparedQuery == null) {
            throw new IllegalArgumentException("preparedQuery 不能为空");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK 必须大于 0");
        }
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

    public static final class PreparedQuery {
        private final RetrievalPlan plan;
        private final float[] embedding;

        private PreparedQuery(RetrievalPlan plan, float[] embedding) {
            this.plan = plan;
            this.embedding = embedding;
        }
    }

    private record RetrievalPlan(
            QueryRewriteResult rewriteResult,
            Set<String> departments,
            boolean historicalCohortQuery,
            boolean crossYearExperienceQuery,
            Integer resolvedYear
    ) {
    }

    @FunctionalInterface
    private interface SearchExecutor {
        List<SearchResultResponse> search(int topK, Filter.Expression filter);
    }
}
