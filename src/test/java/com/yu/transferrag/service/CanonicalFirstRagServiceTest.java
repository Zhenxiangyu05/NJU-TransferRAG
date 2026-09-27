package com.yu.transferrag.service;

import com.yu.transferrag.dto.AnswerabilityResult;
import com.yu.transferrag.dto.RagResponse;
import com.yu.transferrag.dto.SearchResultResponse;
import com.yu.transferrag.dto.SourceResponse;
import com.yu.transferrag.entity.Chunk;
import com.yu.transferrag.entity.Document;
import com.yu.transferrag.entity.DocumentRole;
import com.yu.transferrag.entity.EvidenceRef;
import com.yu.transferrag.repository.DocumentRepository;
import com.yu.transferrag.repository.EvidenceRefRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CanonicalFirstRagServiceTest {

    @Mock
    private RetrievalService retrievalService;
    @Mock
    private AnswerabilityService answerabilityService;
    @Mock
    private ChatModel chatModel;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private EvidenceRefRepository evidenceRefRepository;

    private RagService ragService;
    private RetrievalService.PreparedQuery preparedQuery;

    @BeforeEach
    void setUp() {
        ragService = new RagService(retrievalService, answerabilityService, chatModel,
                documentRepository, evidenceRefRepository, true);
        preparedQuery = mock(RetrievalService.PreparedQuery.class);
    }

    @Test
    void shouldAnswerFromCanonicalKnowledgeAndReturnEvidenceCitation() {
        String question = "软件学院转专业需要面试吗？";
        SearchResultResponse canonical = canonicalResult(900L, "[F0] 需要参加面试", 0.80);
        Document evidence = evidenceDocument(12L, "2026 转专业通知");
        when(retrievalService.prepareCanonicalFirst(question)).thenReturn(preparedQuery);
        when(retrievalService.searchCanonical(preparedQuery, 3)).thenReturn(List.of(canonical));
        when(evidenceRefRepository
                .findByCanonicalChunk_IdInOrderByCanonicalChunk_IdAscFactIndexAscIdAsc(List.of(900L)))
                .thenReturn(List.of(evidenceRef(1L, 900L, 0, evidence, 3, "通知要求参加面试")));
        when(answerabilityService.check(anyString(), anyString(), anyList()))
                .thenReturn(new AnswerabilityResult(true, List.of("S1"), "证据充分"));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("需要参加面试。[S1]"));

        RagResponse response = ragService.ask(question);

        assertEquals("需要参加面试。[S1]", response.getAnswer());
        assertEquals(12L, response.getSources().getFirst().getDocumentId());
        assertEquals(3, response.getSources().getFirst().getSourcePage());
        verify(retrievalService, never()).searchEvidence(any(), any(Integer.class));
        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<SourceResponse>> sources = ArgumentCaptor.forClass(List.class);
        verify(answerabilityService).check(anyString(), context.capture(), sources.capture());
        assertTrue(context.getValue().contains("需要参加面试"));
        assertTrue(context.getValue().contains("通知要求参加面试"));
        assertEquals("S1", sources.getValue().getFirst().getCitationId());
    }

    @Test
    void shouldFallbackWithoutCanonicalAnswerabilityWhenCanonicalIsEmptyOrBelowGate() {
        String question = "问题";
        when(retrievalService.prepareCanonicalFirst(question)).thenReturn(preparedQuery);
        when(retrievalService.searchCanonical(preparedQuery, 3))
                .thenReturn(List.of(canonicalResult(900L, "[F0] 不相关", 0.549)));
        when(retrievalService.searchEvidence(preparedQuery, 3)).thenReturn(List.of());

        RagResponse response = ragService.ask(question);

        assertEquals("根据当前知识库资料无法确定。", response.getAnswer());
        verify(answerabilityService, never()).check(anyString(), anyString(), anyList());
        verify(retrievalService).searchEvidence(preparedQuery, 3);
    }

    @Test
    void shouldFallbackWhenCanonicalSearchIsEmpty() {
        String question = "问题";
        when(retrievalService.prepareCanonicalFirst(question)).thenReturn(preparedQuery);
        when(retrievalService.searchCanonical(preparedQuery, 3)).thenReturn(List.of());
        when(retrievalService.searchEvidence(preparedQuery, 3)).thenReturn(List.of());

        ragService.ask(question);

        verify(answerabilityService, never()).check(anyString(), anyString(), anyList());
        verify(retrievalService).searchEvidence(preparedQuery, 3);
    }

    @Test
    void shouldRunEvidenceGateAnswerabilityAndGenerationAfterFallback() {
        String question = "问题";
        SearchResultResponse evidenceResult = new SearchResultResponse();
        evidenceResult.setDocumentId(12L);
        evidenceResult.setChunkId(120L);
        evidenceResult.setChunkIndex(0);
        evidenceResult.setContent("原始资料正文");
        evidenceResult.setScore(0.75);
        when(retrievalService.prepareCanonicalFirst(question)).thenReturn(preparedQuery);
        when(retrievalService.searchCanonical(preparedQuery, 3)).thenReturn(List.of());
        when(retrievalService.searchEvidence(preparedQuery, 3)).thenReturn(List.of(evidenceResult));
        when(documentRepository.findAllById(java.util.Set.of(12L)))
                .thenReturn(List.of(evidenceDocument(12L, "原始通知")));
        when(answerabilityService.check(anyString(), anyString(), anyList()))
                .thenReturn(new AnswerabilityResult(true, List.of("S1"), "充分"));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("答案。[S1]"));

        RagResponse response = ragService.ask(question);

        assertEquals("答案。[S1]", response.getAnswer());
        assertEquals(12L, response.getSources().getFirst().getDocumentId());
        verify(answerabilityService).check(anyString(), anyString(), anyList());
    }

    @Test
    void shouldFallbackWhenCanonicalAnswerabilityRejectsEvidence() {
        String question = "问题";
        SearchResultResponse canonical = canonicalResult(900L, "[F0] 事实", 0.80);
        Document evidence = evidenceDocument(12L, "通知");
        when(retrievalService.prepareCanonicalFirst(question)).thenReturn(preparedQuery);
        when(retrievalService.searchCanonical(preparedQuery, 3)).thenReturn(List.of(canonical));
        when(evidenceRefRepository
                .findByCanonicalChunk_IdInOrderByCanonicalChunk_IdAscFactIndexAscIdAsc(List.of(900L)))
                .thenReturn(List.of(evidenceRef(1L, 900L, 0, evidence, 3, "证据")));
        when(answerabilityService.check(anyString(), anyString(), anyList()))
                .thenReturn(AnswerabilityResult.notAnswerable("不足"));
        when(retrievalService.searchEvidence(preparedQuery, 3)).thenReturn(List.of());

        ragService.ask(question);

        verify(retrievalService).searchEvidence(preparedQuery, 3);
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void shouldFallbackWhenAnyCanonicalFactHasNoExactEvidenceRef() {
        String question = "问题";
        SearchResultResponse canonical = canonicalResult(900L, "[F0] 事实一\n[F1] 事实二", 0.80);
        Document evidence = evidenceDocument(12L, "通知");
        when(retrievalService.prepareCanonicalFirst(question)).thenReturn(preparedQuery);
        when(retrievalService.searchCanonical(preparedQuery, 3)).thenReturn(List.of(canonical));
        when(evidenceRefRepository
                .findByCanonicalChunk_IdInOrderByCanonicalChunk_IdAscFactIndexAscIdAsc(List.of(900L)))
                .thenReturn(List.of(evidenceRef(1L, 900L, 0, evidence, 3, "事实一证据")));
        when(retrievalService.searchEvidence(preparedQuery, 3)).thenReturn(List.of());

        ragService.ask(question);

        verify(answerabilityService, never()).check(anyString(), anyString(), anyList());
        verify(retrievalService).searchEvidence(preparedQuery, 3);
    }

    @Test
    void shouldDeduplicateEvidenceByDocumentAndPageButKeepDifferentAndNullPages() {
        String question = "问题";
        SearchResultResponse canonical = canonicalResult(
                900L, "[F0] 事实一\n[F1] 事实二\n[F2] 事实三", 0.80);
        Document evidence = evidenceDocument(12L, "通知");
        when(retrievalService.prepareCanonicalFirst(question)).thenReturn(preparedQuery);
        when(retrievalService.searchCanonical(preparedQuery, 3)).thenReturn(List.of(canonical));
        when(evidenceRefRepository
                .findByCanonicalChunk_IdInOrderByCanonicalChunk_IdAscFactIndexAscIdAsc(List.of(900L)))
                .thenReturn(List.of(
                        evidenceRef(1L, 900L, 0, evidence, 3, "证据 A"),
                        evidenceRef(2L, 900L, 0, evidence, 3, "同页重复"),
                        evidenceRef(3L, 900L, 1, evidence, 4, "证据 B"),
                        evidenceRef(4L, 900L, 2, evidence, null, "无页码证据"),
                        evidenceRef(5L, 900L, 2, evidence, null, "无页码重复")
                ));
        when(answerabilityService.check(anyString(), anyString(), anyList()))
                .thenReturn(new AnswerabilityResult(true, List.of("S1", "S2", "S3"), "充分"));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("回答。[S1]"));

        RagResponse response = ragService.ask(question);

        assertEquals(3, response.getSources().size());
        assertEquals(java.util.Arrays.asList(3, 4, null), response.getSources().stream()
                .map(SourceResponse::getSourcePage).toList());
        assertEquals(List.of("S1", "S2", "S3"), response.getSources().stream()
                .map(SourceResponse::getCitationId).toList());
        ArgumentCaptor<String> answerabilityContext = ArgumentCaptor.forClass(String.class);
        verify(answerabilityService).check(anyString(), answerabilityContext.capture(), anyList());
        assertTrue(answerabilityContext.getValue().contains("证据 A"));
        assertTrue(answerabilityContext.getValue().contains("证据 B"));
        assertTrue(answerabilityContext.getValue().contains("无页码证据"));
        assertFalse(answerabilityContext.getValue().contains("同页重复"));
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertFalse(prompt.getValue().getUserMessage().getText().contains("[F0]"));
    }

    private SearchResultResponse canonicalResult(long chunkId, String facts, double score) {
        SearchResultResponse result = new SearchResultResponse();
        result.setChunkId(chunkId);
        result.setDocumentId(90L);
        result.setChunkIndex(0);
        result.setDocumentRole(DocumentRole.CANONICAL);
        result.setSection("申请条件");
        result.setContent("主题：申请条件\n" + facts);
        result.setScore(score);
        result.setChunkDepartment("软件学院");
        result.setPolicyYear(2026);
        result.setEffectiveYear(2026);
        return result;
    }

    private Document evidenceDocument(long id, String title) {
        Document document = new Document();
        document.setId(id);
        document.setTitle(title);
        document.setSourceType("OFFICIAL_PDF");
        document.setDocumentRole(DocumentRole.EVIDENCE);
        document.setDepartment("软件学院");
        document.setYear(2026);
        return document;
    }

    private EvidenceRef evidenceRef(long id, long chunkId, int factIndex, Document evidence,
                                    Integer page, String text) {
        Chunk chunk = new Chunk();
        chunk.setId(chunkId);
        EvidenceRef ref = new EvidenceRef();
        ref.setId(id);
        ref.setCanonicalChunk(chunk);
        ref.setFactIndex(factIndex);
        ref.setEvidenceDocument(evidence);
        ref.setSourcePage(page);
        ref.setEvidenceText(text);
        return ref;
    }

    private ChatResponse chatResponse(String answer) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(answer))));
    }
}
