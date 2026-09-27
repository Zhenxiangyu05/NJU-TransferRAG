package com.yu.transferrag.service;

import com.yu.transferrag.dto.EntityRole;
import com.yu.transferrag.dto.ApplicantStage;
import com.yu.transferrag.dto.MatchedEntity;
import com.yu.transferrag.dto.QueryRewriteResult;
import com.yu.transferrag.dto.ResolvedEntity;
import com.yu.transferrag.dto.SearchResultResponse;
import com.yu.transferrag.repository.ChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RetrievalServiceTest {

    @Mock
    private VectorStore vectorStore;

    @Mock
    private QueryRewriteService queryRewriteService;

    @Mock
    private ChunkRepository chunkRepository;

    @Mock
    private PrecomputedVectorSearch precomputedVectorSearch;

    private RetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        retrievalService = new RetrievalService(
                vectorStore, queryRewriteService, chunkRepository, precomputedVectorSearch
        );
        lenient().when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of());
    }

    @Test
    void shouldUseStrictThenFallbackDepartmentFiltersWithEffectiveYear() {
        String query = "2025年汉语言文学转专业需要什么条件？";
        stubRewrite(query, List.of(new MatchedEntity("汉语言文学", "MAJOR", "文学院")), 2025, false);
        Document fallback = vectorResult(6, 11, null, "汉语言文学");
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of())
                .thenReturn(List.of(fallback));

        List<SearchResultResponse> results = retrievalService.search(query, 3);

        List<SearchRequest> requests = capturedRequests(2);
        assertEquals(expectedDepartmentFilter(Set.of("文学院"), 2025, true),
                requests.get(0).getFilterExpression());
        assertEquals(expectedDepartmentFilter(Set.of("文学院"), 2025, false),
                requests.get(1).getFilterExpression());
        assertEquals(query, requests.get(0).getQuery());
        assertEquals(3, requests.get(0).getTopK());
        assertEquals(List.of(11L), results.stream().map(SearchResultResponse::getChunkId).toList());
    }

    @Test
    void shouldFilterStructuredPolicyByCycleAndCohort() {
        String query = "2026年大一汉语言文学转专业条件";
        when(queryRewriteService.rewriteWithContext(query)).thenReturn(new QueryRewriteResult(
                query, query,
                List.of(new MatchedEntity("汉语言文学", "MAJOR", "文学院")),
                List.of(), List.of("文学院"), List.of("汉语言文学"), List.of(),
                2026, 2026, false, false,
                2026, 2025, ApplicantStage.FIRST_YEAR, true
        ));

        retrievalService.search(query, 3);

        SearchRequest request = capturedRequests(2).getFirst();
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        Filter.Expression expected = builder.and(
                builder.or(
                        builder.eq("department", "文学院"),
                        builder.and(
                                builder.and(builder.eq("scope", "GLOBAL"),
                                        builder.eq("sourceType", "OFFICIAL")),
                                builder.eq("chunkDepartment", "文学院")
                        )
                ),
                builder.and(builder.eq("policyYear", 2026), builder.eq("cohortYear", 2025))
        ).build();
        assertEquals(expectedEvidenceFilter(expected), request.getFilterExpression());
    }

    @Test
    void shouldFilterPolicyCycleWithoutForcingCohort() {
        String query = "2026年汉语言文学转专业条件";
        when(queryRewriteService.rewriteWithContext(query)).thenReturn(new QueryRewriteResult(
                query, query,
                List.of(new MatchedEntity("汉语言文学", "MAJOR", "文学院")),
                List.of(), List.of("文学院"), List.of("汉语言文学"), List.of(),
                2026, 2026, false, false,
                2026, null, null, true
        ));

        retrievalService.search(query, 3);

        SearchRequest request = capturedRequests(2).getFirst();
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        Filter.Expression expected = builder.and(
                builder.or(
                        builder.eq("department", "文学院"),
                        builder.and(
                                builder.and(builder.eq("scope", "GLOBAL"),
                                        builder.eq("sourceType", "OFFICIAL")),
                                builder.eq("chunkDepartment", "文学院")
                        )
                ),
                builder.eq("policyYear", 2026)
        ).build();
        assertEquals(expectedEvidenceFilter(expected), request.getFilterExpression());
    }

    @Test
    void shouldPreferExplicitHistoricalCohortOverEffectiveYear() {
        String query = "2023级人文大类分流情况";
        when(queryRewriteService.rewriteWithContext(query)).thenReturn(historicalCohortRewrite(
                query, 2023
        ));

        retrievalService.search(query, 3);

        SearchRequest request = capturedRequests(2).getFirst();
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        assertEquals(expectedEvidenceFilter(builder.eq("cohortYear", 2023).build()), request.getFilterExpression());
        verifyNoInteractions(chunkRepository);
    }

    @Test
    void shouldFallbackOnlyToUntaggedCandidatesWhenCohortSearchIsEmpty() {
        String query = "2023级人文大类分流情况";
        when(queryRewriteService.rewriteWithContext(query)).thenReturn(historicalCohortRewrite(
                query, 2023
        ));
        Document untagged = Document.builder()
                .text("untagged historical guide")
                .metadata("chunkId", 313)
                .metadata("documentId", 28)
                .metadata("chunkIndex", 0)
                .metadata("effectiveYear", 2026)
                .score(0.70)
                .build();
        Document wrongTagged = Document.builder()
                .text("wrong cohort")
                .metadata("chunkId", 999)
                .metadata("documentId", 28)
                .metadata("chunkIndex", 9)
                .metadata("cohortYear", 2024)
                .metadata("effectiveYear", 2026)
                .score(0.80)
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of())
                .thenReturn(List.of(wrongTagged, untagged));

        List<SearchResultResponse> results = retrievalService.search(query, 3);

        List<SearchRequest> requests = capturedRequests(2);
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        assertEquals(expectedEvidenceFilter(builder.eq("cohortYear", 2023).build()),
                requests.getFirst().getFilterExpression());
        assertEquals(expectedEvidenceFilter(null), requests.get(1).getFilterExpression());
        assertEquals(List.of(313L), results.stream().map(SearchResultResponse::getChunkId).toList());
    }

    @Test
    void shouldUseOnlyResolvedTargetDepartmentsForStrictFilter() {
        String query = "软院之外，电子学院转专业有什么要求？";
        when(queryRewriteService.rewriteWithContext(query)).thenReturn(new QueryRewriteResult(
                query,
                "软院（软件学院）之外，电子学院（电子科学与工程学院）转专业有什么要求？",
                List.of(
                        new MatchedEntity("软件学院", "DEPARTMENT", "软件学院"),
                        new MatchedEntity("电子科学与工程学院", "DEPARTMENT", "电子科学与工程学院")
                ),
                List.of(
                        new ResolvedEntity("软件学院", "DEPARTMENT", "软件学院", "软院", EntityRole.EXCLUDED),
                        new ResolvedEntity("电子科学与工程学院", "DEPARTMENT", "电子科学与工程学院", "电子学院", EntityRole.TARGET)
                ),
                List.of("电子科学与工程学院"),
                List.of(),
                List.of(),
                2026,
                2026,
                false,
                false
        ));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                vectorResult(14, 98, "电子科学与工程学院", "电子信息类")
        ));

        retrievalService.search(query, 3);

        SearchRequest request = capturedRequests(1).getFirst();
        assertEquals(expectedDepartmentFilter(Set.of("电子科学与工程学院"), 2026, true),
                request.getFilterExpression());
    }

    @Test
    void shouldNotExecuteFallbackWhenStrictResultsReachTopK() {
        String query = "2025年法学转专业条件";
        stubRewrite(query, List.of(new MatchedEntity("法学", "MAJOR", "法学院")), 2025, false);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                vectorResult(6, 1, "法学院", "法学"),
                vectorResult(20, 2, null, "法学"),
                vectorResult(21, 3, null, "知识产权")
        ));

        List<SearchResultResponse> results = retrievalService.search(query, 3);

        assertEquals(3, results.size());
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void shouldReturnOneStrictResultWithoutFallbackWhenTopKIsThree() {
        String query = "2025年汉语言文学转专业需要什么条件？";
        stubRewrite(query, List.of(new MatchedEntity("汉语言文学", "MAJOR", "文学院")), 2025, false);
        Document strict = vectorResult(6, 11, "文学院", "汉语言文学");
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(strict));

        List<SearchResultResponse> results = retrievalService.search(query, 3);

        assertEquals(List.of(11L),
                results.stream().map(SearchResultResponse::getChunkId).toList());
        assertEquals("文学院", results.get(0).getChunkDepartment());
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void shouldReturnTwoStrictResultsWithoutFallbackWhenTopKIsFive() {
        String query = "2025年法学转专业条件";
        stubRewrite(query, List.of(new MatchedEntity("法学", "MAJOR", "法学院")), 2025, false);
        Document first = vectorResult(6, 11, "法学院", "法学");
        Document second = vectorResult(20, 12, "法学院", "法学");
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(first, second));

        List<SearchResultResponse> results = retrievalService.search(query, 5);

        assertEquals(2, results.size());
        assertEquals(List.of(11L, 12L),
                results.stream().map(SearchResultResponse::getChunkId).toList());
        verify(vectorStore).similaritySearch(any(SearchRequest.class));
    }

    @Test
    void shouldUseOnePhaseSearchWhenNoDepartmentIsRecognized() {
        String query = "转专业一般需要什么条件？";
        when(queryRewriteService.rewriteWithContext(query)).thenReturn(new QueryRewriteResult(
                query,
                query,
                List.of()
        ));
        when(chunkRepository.findMaxEffectiveYear()).thenReturn(2026);

        retrievalService.search(query, 3);

        List<SearchRequest> requests = capturedRequests(1);
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        assertEquals(expectedEvidenceFilter(builder.eq("effectiveYear", 2026).build()),
                requests.get(0).getFilterExpression());
    }

    @Test
    void shouldSupportMultipleTargetDepartmentsInStrictFilter() {
        String query = "2025年文学院和法学院转专业有什么区别？";
        stubRewrite(query, List.of(
                new MatchedEntity("汉语言文学", "MAJOR", "文学院"),
                new MatchedEntity("法学", "MAJOR", "法学院")
        ), 2025, false);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                vectorResult(6, 1, "文学院", "汉语言文学")
        ));

        retrievalService.search(query, 1);

        List<SearchRequest> requests = capturedRequests(1);
        assertEquals(expectedDepartmentFilter(
                        new LinkedHashSet<>(List.of("文学院", "法学院")),
                        2025,
                        true
                ),
                requests.get(0).getFilterExpression());
    }

    @Test
    void shouldKeepDepartmentFilterButSkipYearForMultiYearQuery() {
        String query = "2025和2026软件学院转专业政策有什么区别？";
        when(queryRewriteService.rewriteWithContext(query)).thenReturn(new QueryRewriteResult(
                query,
                query,
                List.of(new MatchedEntity("软件学院", "DEPARTMENT", "软件学院")),
                null,
                null,
                true
        ));
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                vectorResult(10, 1, null, "软件工程")
        ));

        retrievalService.search(query, 1);

        List<SearchRequest> requests = capturedRequests(1);
        assertEquals(expectedDepartmentFilter(Set.of("软件学院"), null, true),
                requests.get(0).getFilterExpression());
        verifyNoInteractions(chunkRepository);
    }

    @Test
    void shouldResolveLatestEffectiveYearBeforeBothDepartmentSearches() {
        String query = "软件学院转专业需要什么条件？";
        when(queryRewriteService.rewriteWithContext(query)).thenReturn(new QueryRewriteResult(
                query,
                query,
                List.of(new MatchedEntity("软件学院", "DEPARTMENT", "软件学院"))
        ));
        when(chunkRepository.findMaxEffectiveYearForDepartmentsOrGlobalOfficial(
                Set.of("软件学院"), "GLOBAL", "OFFICIAL"
        )).thenReturn(2026);

        retrievalService.search(query, 3);

        List<SearchRequest> requests = capturedRequests(2);
        assertEquals(expectedDepartmentFilter(Set.of("软件学院"), 2026, true),
                requests.get(0).getFilterExpression());
        assertEquals(expectedDepartmentFilter(Set.of("软件学院"), 2026, false),
                requests.get(1).getFilterExpression());
    }

    @Test
    void shouldKeepTwoPhaseDepartmentSearchWhenLatestYearIsUnavailable() {
        String query = "软件学院转专业需要什么条件？";
        when(queryRewriteService.rewriteWithContext(query)).thenReturn(new QueryRewriteResult(
                query,
                query,
                List.of(new MatchedEntity("软件学院", "DEPARTMENT", "软件学院"))
        ));
        when(chunkRepository.findMaxEffectiveYearForDepartmentsOrGlobalOfficial(
                Set.of("软件学院"), "GLOBAL", "OFFICIAL"
        )).thenReturn(null);

        retrievalService.search(query, 3);

        List<SearchRequest> requests = capturedRequests(2);
        assertEquals(expectedDepartmentFilter(Set.of("软件学院"), null, true),
                requests.get(0).getFilterExpression());
        assertEquals(expectedDepartmentFilter(Set.of("软件学院"), null, false),
                requests.get(1).getFilterExpression());
    }

    @Test
    void shouldReturnChunkLevelPolicyMetadataForDebugSearch() {
        String query = "2025年汉语言文学转专业需要什么条件？";
        stubRewrite(query, List.of(new MatchedEntity("汉语言文学", "MAJOR", "文学院")), 2025, false);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                vectorResult(6, 43, "文学院", "汉语言文学"),
                vectorResult(6, 44, "文学院", "汉语言文学"),
                vectorResult(6, 45, "文学院", "汉语言文学")
        ));

        List<SearchResultResponse> results = retrievalService.search(query, 3);

        assertEquals(6L, results.get(0).getDocumentId());
        assertEquals(2025, results.get(0).getPolicyYear());
        assertEquals(2025, results.get(0).getEffectiveYear());
        assertEquals("文学院", results.get(0).getChunkDepartment());
        assertEquals("汉语言文学", results.get(0).getMajor());
    }

    @Test
    void shouldSearchExperienceAcrossYearsWhenNoYearIsSpecified() {
        String query = "数学学院保研有什么经验？";
        stubRewrite(
                query,
                List.of(new MatchedEntity("数学学院", "DEPARTMENT", "数学学院")),
                null,
                null,
                false,
                true
        );
        when(chunkRepository.findMaxEffectiveYearForDepartmentsOrGlobalOfficial(
                Set.of("数学学院"), "GLOBAL", "OFFICIAL"
        )).thenReturn(2026);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                vectorResult(30, 301, "数学学院", "数学类", 2025, 0.72)
        ));

        List<SearchResultResponse> results = retrievalService.search(query, 3);

        List<SearchRequest> requests = capturedRequests(1);
        assertEquals(expectedDepartmentFilter(Set.of("数学学院"), null, true),
                requests.getFirst().getFilterExpression());
        assertEquals(10, requests.getFirst().getTopK());
        assertEquals(List.of(301L), results.stream().map(SearchResultResponse::getChunkId).toList());
    }

    @Test
    void shouldKeepExplicitYearFilterForExperienceQuery() {
        String query = "2025年数学学院保研有什么经验？";
        stubRewrite(
                query,
                List.of(new MatchedEntity("数学学院", "DEPARTMENT", "数学学院")),
                2025,
                2025,
                false,
                true
        );
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                vectorResult(30, 301, "数学学院", "数学类", 2025, 0.72)
        ));

        retrievalService.search(query, 3);

        List<SearchRequest> requests = capturedRequests(1);
        assertEquals(expectedDepartmentFilter(Set.of("数学学院"), 2025, true),
                requests.getFirst().getFilterExpression());
        assertEquals(3, requests.getFirst().getTopK());
        verifyNoInteractions(chunkRepository);
    }

    @Test
    void shouldKeepCurrentYearFilterForCurrentYearExperienceQuery() {
        String query = "今年数学学院保研有什么经验？";
        int currentYear = LocalDate.now().getYear();
        stubRewrite(
                query,
                List.of(new MatchedEntity("数学学院", "DEPARTMENT", "数学学院")),
                null,
                currentYear,
                false,
                true
        );
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                vectorResult(30, 301, "数学学院", "数学类", currentYear, 0.72)
        ));

        retrievalService.search(query, 3);

        List<SearchRequest> requests = capturedRequests(1);
        assertEquals(expectedDepartmentFilter(Set.of("数学学院"), currentYear, true),
                requests.getFirst().getFilterExpression());
        assertEquals(3, requests.getFirst().getTopK());
        verifyNoInteractions(chunkRepository);
    }

    @Test
    void shouldKeepMultiYearExperienceQueryWithoutSingleYearFilter() {
        String query = "2024和2025年数学学院保研经验有什么区别？";
        stubRewrite(
                query,
                List.of(new MatchedEntity("数学学院", "DEPARTMENT", "数学学院")),
                null,
                null,
                true,
                true
        );
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                vectorResult(30, 301, "数学学院", "数学类", 2025, 0.72)
        ));

        retrievalService.search(query, 3);

        List<SearchRequest> requests = capturedRequests(1);
        assertEquals(expectedDepartmentFilter(Set.of("数学学院"), null, true),
                requests.getFirst().getFilterExpression());
        assertEquals(3, requests.getFirst().getTopK());
        verifyNoInteractions(chunkRepository);
    }

    @Test
    void shouldKeepClearlyMoreRelevantOlderExperienceAheadOfNewerResult() {
        String query = "数学学院保研有什么经验？";
        stubCrossYearExperience(query);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                vectorResult(30, 301, "数学学院", "数学类", 2026, 0.60),
                vectorResult(31, 302, "数学学院", "数学类", 2025, 0.72)
        ));

        List<SearchResultResponse> results = retrievalService.search(query, 2);

        assertEquals(List.of(302L, 301L),
                results.stream().map(SearchResultResponse::getChunkId).toList());
    }

    @Test
    void shouldPreferNewerExperienceWhenVectorScoresAreClose() {
        String query = "数学学院保研有什么经验？";
        stubCrossYearExperience(query);
        when(vectorStore.similaritySearch(any(SearchRequest.class))).thenReturn(List.of(
                vectorResult(31, 302, "数学学院", "数学类", 2025, 0.705),
                vectorResult(30, 301, "数学学院", "数学类", 2026, 0.70)
        ));

        List<SearchResultResponse> results = retrievalService.search(query, 2);

        assertEquals(List.of(301L, 302L),
                results.stream().map(SearchResultResponse::getChunkId).toList());
        assertEquals(0.70, results.getFirst().getScore());
    }

    @Test
    void shouldReuseOneEmbeddingAndKeepCanonicalAndEvidenceRolesSeparate() {
        String query = "软件学院转专业条件";
        when(queryRewriteService.rewriteWithContext(query)).thenReturn(new QueryRewriteResult(
                query, query, List.of()
        ));
        when(chunkRepository.findMaxEffectiveYear()).thenReturn(2026);
        float[] embedding = new float[]{0.1f, 0.2f};
        when(precomputedVectorSearch.embed(query)).thenReturn(embedding);
        when(precomputedVectorSearch.search(any(float[].class), any(Integer.class), any(Filter.Expression.class)))
                .thenReturn(List.of(vectorMatch(1L, 101L, "CANONICAL", 0.80)))
                .thenReturn(List.of(
                        vectorMatch(2L, 201L, null, 0.75),
                        vectorMatch(1L, 101L, "CANONICAL", 0.80),
                        vectorMatch(3L, 301L, "EVIDENCE", 0.70)
                ));

        RetrievalService.PreparedQuery prepared = retrievalService.prepareCanonicalFirst(query);
        List<SearchResultResponse> canonical = retrievalService.searchCanonical(prepared, 3);
        List<SearchResultResponse> evidence = retrievalService.searchEvidence(prepared, 3);

        assertEquals(List.of(101L), canonical.stream().map(SearchResultResponse::getChunkId).toList());
        assertEquals(List.of(201L, 301L), evidence.stream().map(SearchResultResponse::getChunkId).toList());
        verify(precomputedVectorSearch).embed(query);
        ArgumentCaptor<Filter.Expression> filterCaptor = ArgumentCaptor.forClass(Filter.Expression.class);
        verify(precomputedVectorSearch, times(2)).search(
                any(float[].class), any(Integer.class), filterCaptor.capture());
        assertEquals(true, filterCaptor.getAllValues().getFirst().toString()
                .contains("documentRole"));
        assertEquals(true, filterCaptor.getAllValues().get(1).toString()
                .contains("documentRole"));
    }

    @Test
    void shouldKeepDepartmentPolicyAndCohortFiltersForBothV2Layers() {
        String query = "2026年大一软件学院转专业条件";
        when(queryRewriteService.rewriteWithContext(query)).thenReturn(new QueryRewriteResult(
                query, query,
                List.of(new MatchedEntity("软件学院", "DEPARTMENT", "软件学院")),
                List.of(), List.of("软件学院"), List.of(), List.of(),
                2026, 2026, false, false,
                2026, 2025, ApplicantStage.FIRST_YEAR, true
        ));
        when(precomputedVectorSearch.embed(query)).thenReturn(new float[]{0.1f});
        when(precomputedVectorSearch.search(any(float[].class), any(Integer.class),
                any(Filter.Expression.class)))
                .thenReturn(List.of(vectorMatch(
                        90L, 900L, "CANONICAL", 0.80,
                        "软件学院", "DEPARTMENT", "软件学院"
                )))
                .thenReturn(List.of(vectorMatch(
                        12L, 120L, null, 0.75,
                        "软件学院", "DEPARTMENT", "软件学院"
                )));

        RetrievalService.PreparedQuery prepared = retrievalService.prepareCanonicalFirst(query);
        retrievalService.searchCanonical(prepared, 3);
        retrievalService.searchEvidence(prepared, 3);

        ArgumentCaptor<Filter.Expression> filters = ArgumentCaptor.forClass(Filter.Expression.class);
        verify(precomputedVectorSearch, times(2)).search(
                any(float[].class), any(Integer.class), filters.capture());
        String canonicalFilter = filters.getAllValues().get(0).toString();
        String evidenceFilter = filters.getAllValues().get(1).toString();
        for (String expected : List.of("department", "软件学院", "policyYear", "2026",
                "cohortYear", "2025")) {
            assertEquals(true, canonicalFilter.contains(expected));
            assertEquals(true, evidenceFilter.contains(expected));
        }
        assertEquals(true, canonicalFilter.contains("documentRole"));
        assertEquals(true, evidenceFilter.contains("documentRole"));
    }

    private void stubRewrite(String query,
                             List<MatchedEntity> entities,
                             Integer resolvedYear,
                             boolean multiYear) {
        when(queryRewriteService.rewriteWithContext(query)).thenReturn(new QueryRewriteResult(
                query,
                query,
                entities,
                resolvedYear,
                resolvedYear,
                multiYear
        ));
    }

    private void stubRewrite(String query,
                             List<MatchedEntity> entities,
                             Integer explicitYear,
                             Integer resolvedYear,
                             boolean multiYear,
                             boolean experienceQuery) {
        when(queryRewriteService.rewriteWithContext(query)).thenReturn(new QueryRewriteResult(
                query,
                query,
                entities,
                explicitYear,
                resolvedYear,
                multiYear,
                experienceQuery
        ));
    }

    private void stubCrossYearExperience(String query) {
        stubRewrite(
                query,
                List.of(new MatchedEntity("数学学院", "DEPARTMENT", "数学学院")),
                null,
                null,
                false,
                true
        );
        when(chunkRepository.findMaxEffectiveYearForDepartmentsOrGlobalOfficial(
                Set.of("数学学院"), "GLOBAL", "OFFICIAL"
        )).thenReturn(2026);
    }

    private QueryRewriteResult historicalCohortRewrite(String query, int cohortYear) {
        return new QueryRewriteResult(
                query, query, List.of(), List.of(), List.of(), List.of(), List.of(),
                cohortYear, cohortYear, false, false,
                null, cohortYear, null, false
        );
    }

    private List<SearchRequest> capturedRequests(int expectedCalls) {
        ArgumentCaptor<SearchRequest> captor = ArgumentCaptor.forClass(SearchRequest.class);
        verify(vectorStore, times(expectedCalls)).similaritySearch(captor.capture());
        return captor.getAllValues();
    }

    private Filter.Expression expectedDepartmentFilter(Set<String> departments,
                                                       Integer effectiveYear,
                                                       boolean strict) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op documentDepartments = anyDepartment(
                builder,
                "department",
                departments
        );
        FilterExpressionBuilder.Op globalOfficial = builder.and(
                builder.eq("scope", "GLOBAL"),
                builder.eq("sourceType", "OFFICIAL")
        );
        if (strict) {
            globalOfficial = builder.and(
                    globalOfficial,
                    anyDepartment(builder, "chunkDepartment", departments)
            );
        }

        FilterExpressionBuilder.Op combined = builder.or(documentDepartments, globalOfficial);
        if (effectiveYear != null) {
            combined = builder.and(combined, builder.eq("effectiveYear", effectiveYear));
        }
        return expectedEvidenceFilter(combined.build());
    }

    @Test
    void shouldExcludeCanonicalFromV1WhileKeepingLegacyEvidence() {
        String query = "转专业申请条件";
        when(queryRewriteService.rewriteWithContext(query)).thenReturn(new QueryRewriteResult(
                query, query, List.of()
        ));
        when(chunkRepository.findMaxEffectiveYear()).thenReturn(2026);
        Document canonical = Document.builder()
                .text("[F0] canonical fact")
                .metadata("chunkId", 21)
                .metadata("documentId", 6)
                .metadata("chunkIndex", 0)
                .metadata("effectiveYear", 2026)
                .metadata("documentRole", "CANONICAL")
                .build();
        Document legacyEvidence = Document.builder()
                .text("original evidence")
                .metadata("chunkId", 11)
                .metadata("documentId", 4)
                .metadata("chunkIndex", 0)
                .metadata("effectiveYear", 2026)
                .build();
        when(vectorStore.similaritySearch(any(SearchRequest.class)))
                .thenReturn(List.of(canonical, legacyEvidence));

        List<SearchResultResponse> results = retrievalService.search(query, 3);

        assertEquals(List.of(11L), results.stream().map(SearchResultResponse::getChunkId).toList());
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        assertEquals(expectedEvidenceFilter(builder.eq("effectiveYear", 2026).build()),
                capturedRequests(1).getFirst().getFilterExpression());
    }

    private Filter.Expression expectedEvidenceFilter(Filter.Expression filter) {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();
        FilterExpressionBuilder.Op roleFilter = builder.ne("documentRole", "CANONICAL");
        return filter == null
                ? roleFilter.build()
                : builder.and(roleFilter, new FilterExpressionBuilder.Op(filter)).build();
    }

    private FilterExpressionBuilder.Op anyDepartment(FilterExpressionBuilder builder,
                                                     String field,
                                                     Set<String> departments) {
        FilterExpressionBuilder.Op result = null;
        for (String department : departments) {
            FilterExpressionBuilder.Op current = builder.eq(field, department);
            result = result == null ? current : builder.or(result, current);
        }
        return result;
    }

    private Document vectorResult(long documentId,
                                  long chunkId,
                                  String chunkDepartment,
                                  String major) {
        return vectorResult(documentId, chunkId, chunkDepartment, major, 2025, null);
    }

    private Document vectorResult(long documentId,
                                  long chunkId,
                                  String chunkDepartment,
                                  String major,
                                  int effectiveYear,
                                  Double score) {
        Document.Builder builder = Document.builder()
                .text("chunk-" + chunkId)
                .metadata("chunkId", chunkId)
                .metadata("documentId", documentId)
                .metadata("chunkIndex", Math.toIntExact(chunkId))
                .metadata("policyYear", effectiveYear)
                .metadata("effectiveYear", effectiveYear)
                .metadata("major", major);
        if (score != null) {
            builder.score(score);
        }
        if (chunkDepartment != null) {
            builder.metadata("chunkDepartment", chunkDepartment);
        }
        return builder.build();
    }

    private PrecomputedVectorSearch.VectorMatch vectorMatch(long documentId,
                                                            long chunkId,
                                                            String role,
                                                            double score) {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("documentId", documentId);
        metadata.put("chunkId", chunkId);
        metadata.put("chunkIndex", Math.toIntExact(chunkId));
        metadata.put("effectiveYear", 2026);
        if (role != null) {
            metadata.put("documentRole", role);
        }
        return new PrecomputedVectorSearch.VectorMatch("chunk-" + chunkId, metadata, score);
    }

    private PrecomputedVectorSearch.VectorMatch vectorMatch(long documentId,
                                                            long chunkId,
                                                            String role,
                                                            double score,
                                                            String documentDepartment,
                                                            String scope,
                                                            String chunkDepartment) {
        Map<String, Object> metadata = new java.util.LinkedHashMap<>();
        metadata.put("documentId", documentId);
        metadata.put("chunkId", chunkId);
        metadata.put("chunkIndex", Math.toIntExact(chunkId));
        metadata.put("policyYear", 2026);
        metadata.put("cohortYear", 2025);
        metadata.put("effectiveYear", 2026);
        metadata.put("department", documentDepartment);
        metadata.put("scope", scope);
        metadata.put("chunkDepartment", chunkDepartment);
        if (role != null) {
            metadata.put("documentRole", role);
        }
        return new PrecomputedVectorSearch.VectorMatch("chunk-" + chunkId, metadata, score);
    }
}
