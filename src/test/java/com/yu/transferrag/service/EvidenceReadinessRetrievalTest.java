package com.yu.transferrag.service;

import com.yu.transferrag.dto.*;
import com.yu.transferrag.entity.*;
import com.yu.transferrag.repository.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.vectorstore.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EvidenceReadinessRetrievalTest {
    @ParameterizedTest @ValueSource(booleans = {false,true})
    void overFetchFiltersBeforeTopKAndReusesEmbedding(boolean prepared) {
        var documents = mock(DocumentRepository.class);
        var rows = new ArrayList<DocumentReadinessView>();
        var points = new ArrayList<org.springframework.ai.document.Document>();
        var matches = new ArrayList<PrecomputedVectorSearch.VectorMatch>();
        for (long id = 1; id <= 8; id++) {
            var row = mock(DocumentReadinessView.class);
            lenient().when(row.getDocumentId()).thenReturn(id);
            lenient().when(row.getDocumentRole()).thenReturn(DocumentRole.EVIDENCE);
            lenient().when(row.getTaskId()).thenReturn(id);
            lenient().when(row.getTaskKind()).thenReturn(ImportTaskKind.NEW_EVIDENCE);
            lenient().when(row.getStatus()).thenReturn(id <= 5 ? ImportTaskStatus.PROCESSING : ImportTaskStatus.SUCCEEDED);
            rows.add(row);
            Map<String,Object> metadata = Map.of("documentId",id,"chunkId",id,"documentRole","EVIDENCE","year",2026);
            points.add(org.springframework.ai.document.Document.builder().text("test "+id).metadata(metadata).score(0.99).build());
            matches.add(new PrecomputedVectorSearch.VectorMatch("test "+id,metadata,0.99));
        }
        when(documents.findReadinessByIds(anyCollection())).thenReturn(rows);
        var store = mock(VectorStore.class); var precomputed = mock(PrecomputedVectorSearch.class);
        var rewrite = mock(QueryRewriteService.class);
        when(rewrite.rewriteWithContext("test")).thenReturn(new QueryRewriteResult("test","test",List.of(),2026,2026,false));
        var service = new RetrievalService(store,rewrite,mock(ChunkRepository.class),precomputed,new EvidenceReadinessService(documents));
        List<SearchResultResponse> results;
        if (prepared) {
            when(precomputed.embed("test")).thenReturn(new float[]{1,0,0});
            when(precomputed.search(any(),anyInt(),any())).thenReturn(matches);
            results = service.searchEvidence(service.prepareCanonicalFirst("test"),3);
            verify(precomputed,times(1)).embed("test");
            verify(precomputed).search(any(),intThat(k -> k >= 8 && k <= 256),any());
        } else {
            when(store.similaritySearch(any(SearchRequest.class))).thenReturn(points);
            results = service.search("test",3);
            verify(store,times(1)).similaritySearch(argThat((SearchRequest request) -> request.getTopK() >= 8 && request.getTopK() <= 256));
        }
        assertEquals(List.of(6L,7L,8L),results.stream().map(SearchResultResponse::getDocumentId).toList());
    }
}
