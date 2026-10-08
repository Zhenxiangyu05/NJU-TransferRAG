package com.yu.transferrag.service;

import com.yu.transferrag.dto.*;
import com.yu.transferrag.entity.*;
import com.yu.transferrag.repository.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vectorstore.VectorStore;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Previous leak reproducer, now verifying the real gate. Mocked IO: unit regression only.
 */
class IncompleteEvidenceVisibilityCharacterizationTest {
    @ParameterizedTest
    @CsvSource({"QUEUED,false","PROCESSING,false","FAILED,false","LEGACY,false","SUCCEEDED,false",
                "QUEUED,true","PROCESSING,true","FAILED,true","LEGACY,true","SUCCEEDED,true",
                "VERIFY_PROCESSING,false","VERIFY_FAILED,false","VERIFY_PROCESSING,true","VERIFY_FAILED,true",
                "UNKNOWN,false","UNKNOWN,true","DB_FAILURE,false","DB_FAILURE,true"})
    void onlyPublishedPointsReachLlmContextsAndCitations(String taskState, boolean canonicalFirst) {
        String query = "2026年测试资料有哪些要求？";
        String content = "ISOLATED_PARTIAL_EVIDENCE_MARKER：测试资料要求提交材料。";
        Document document = new Document(); document.setId(9001L); document.setTitle("isolated fixture");
        document.setSourceType("PERSONAL"); document.setDocumentRole(DocumentRole.EVIDENCE); document.setYear(2026);
        var documents = mock(DocumentRepository.class);
        var row = mock(DocumentReadinessView.class);
        when(row.getDocumentId()).thenReturn(9001L);
        when(row.getDocumentRole()).thenReturn(DocumentRole.EVIDENCE);
        when(row.getTaskId()).thenReturn(null);
        if (!taskState.equals("LEGACY") && !taskState.equals("DB_FAILURE")) {
            when(row.getTaskId()).thenReturn(1L);
            when(row.getTaskKind()).thenReturn(taskState.equals("UNKNOWN") ? ImportTaskKind.UNKNOWN
                    : taskState.startsWith("VERIFY_") ? ImportTaskKind.LEGACY_VERIFICATION : ImportTaskKind.NEW_EVIDENCE);
            lenient().when(row.getStatus()).thenReturn(taskState.equals("UNKNOWN") ? ImportTaskStatus.SUCCEEDED
                    : ImportTaskStatus.valueOf(taskState.replace("VERIFY_", "")));
        }
        if (taskState.equals("DB_FAILURE")) {
            when(documents.findReadinessByIds(anyCollection())).thenThrow(new IllegalStateException("isolated DB error"));
        } else when(documents.findReadinessByIds(anyCollection())).thenReturn(List.of(row));
        var readiness = new EvidenceReadinessService(documents);
        var vectors = mock(VectorStore.class);
        var rewrite = mock(QueryRewriteService.class);
        var chunks = mock(ChunkRepository.class);
        var precomputed = mock(PrecomputedVectorSearch.class);
        Map<String,Object> metadata = Map.of("documentId","9001","chunkId","9002","chunkIndex",0,
                "documentRole","EVIDENCE","year",2026,"effectiveYear",2026);
        when(rewrite.rewriteWithContext(query)).thenReturn(new QueryRewriteResult(query,query,List.of(),2026,2026,false));
        if (canonicalFirst) {
            when(precomputed.embed(anyString())).thenReturn(new float[]{1,0,0});
            when(precomputed.search(any(),anyInt(),any())).thenReturn(List.of(
                    new PrecomputedVectorSearch.VectorMatch(content,metadata,0.99)));
        } else {
            when(vectors.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class))).thenReturn(List.of(
                    org.springframework.ai.document.Document.builder().text(content).metadata(metadata).score(0.99).build()));
        }
        var retrieval = new RetrievalService(vectors,rewrite,chunks,precomputed,readiness);
        lenient().when(documents.findAllById(any())).thenReturn(List.of(document));
        var answerability = mock(AnswerabilityService.class);
        lenient().when(answerability.check(eq(query),anyString(),anyList())).thenReturn(new AnswerabilityResult(true,List.of("S1"),"test only"));
        var chat = mock(ChatModel.class);
        lenient().when(chat.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("test only")))));
        var rag = new RagService(retrieval,answerability,chat,documents,mock(EvidenceRefRepository.class),canonicalFirst,readiness);
        if (taskState.equals("DB_FAILURE")) {
            var error = assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> rag.ask(query));
            assertEquals(503,error.getStatusCode().value());
            verifyNoInteractions(answerability,chat);
            return;
        }
        var response = rag.ask(query);
        boolean published = taskState.equals("LEGACY") || taskState.equals("SUCCEEDED") || taskState.startsWith("VERIFY_");
        if (!published) {
            verifyNoInteractions(answerability, chat);
            assertTrue(response.getSources().isEmpty());
            return;
        }
        var context = ArgumentCaptor.forClass(String.class);
        verify(answerability).check(eq(query),context.capture(),anyList());
        assertTrue(context.getValue().contains(content));
        var prompt = ArgumentCaptor.forClass(Prompt.class); verify(chat).call(prompt.capture());
        assertTrue(prompt.getValue().getInstructions().stream().anyMatch(message -> message.getText().contains(content)));
        assertEquals(9001L,response.getSources().getFirst().getDocumentId());
    }
}
