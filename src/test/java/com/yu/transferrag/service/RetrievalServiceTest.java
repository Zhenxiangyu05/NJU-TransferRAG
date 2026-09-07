package com.yu.transferrag.service;

import com.yu.transferrag.dto.MatchedEntity;
import com.yu.transferrag.dto.QueryRewriteResult;
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

import java.util.LinkedHashSet;
import java.util.List;
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

    private RetrievalService retrievalService;

    @BeforeEach
    void setUp() {
        retrievalService = new RetrievalService(vectorStore, queryRewriteService, chunkRepository);
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
        assertEquals(builder.eq("effectiveYear", 2026).build(),
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
        return combined.build();
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
        Document.Builder builder = Document.builder()
                .text("chunk-" + chunkId)
                .metadata("chunkId", chunkId)
                .metadata("documentId", documentId)
                .metadata("chunkIndex", Math.toIntExact(chunkId))
                .metadata("policyYear", 2025)
                .metadata("effectiveYear", 2025)
                .metadata("major", major);
        if (chunkDepartment != null) {
            builder.metadata("chunkDepartment", chunkDepartment);
        }
        return builder.build();
    }
}
