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
import static org.mockito.Mockito.times;
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
    void evaluationTraceCapturesActualCanonicalAndEvidenceContextsWithoutChangingResponse() {
        String question = "软件学院转专业需要面试吗？";
        SearchResultResponse canonical = canonicalResult(900L, "[F0] 需要参加面试", 0.80);
        canonical.setDocumentId(99L);
        canonical.setSection("考核方式");
        Document evidence = evidenceDocument(12L, "2026 转专业通知");
        when(retrievalService.prepareCanonicalFirst(question)).thenReturn(preparedQuery);
        when(retrievalService.searchCanonical(preparedQuery, 3)).thenReturn(List.of(canonical));
        when(evidenceRefRepository
                .findByCanonicalChunk_IdInOrderByCanonicalChunk_IdAscFactIndexAscIdAsc(List.of(900L)))
                .thenReturn(List.of(evidenceRef(1L, 900L, 0, evidence, 3, "通知要求参加面试")));
        when(answerabilityService.check(anyString(), anyString(), anyList()))
                .thenReturn(new AnswerabilityResult(true, List.of("S1"), "证据充分"));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("需要参加面试。[S1]"));

        RagService.EvaluationTraceResult result = ragService.askForEvaluation(question, true);

        assertEquals("需要参加面试。[S1]", result.response().getAnswer());
        assertEquals("CANONICAL", result.trace().retrievalLayer());
        assertFalse(result.trace().fallback());
        assertEquals(1, result.trace().canonicalRanking().size());
        assertEquals(12L, result.trace().evidenceEquivalentRanking().getFirst().documentId());
        assertEquals(List.of(12L), result.trace().finalCitationDocumentIds());
        assertEquals(1, result.trace().retrievedContexts().size());
        assertEquals(1, result.trace().approvedContexts().size());
        assertTrue(result.trace().retrievedContexts().getFirst().contains("[F0] 需要参加面试"));
        assertTrue(result.trace().approvedContexts().getFirst().contains("通知要求参加面试"));
    }

    @Test
    void evidenceEquivalentRankingDeduplicatesEvidenceDocumentsAtFirstCanonicalRank() {
        SearchResultResponse first = canonicalResult(901L, "[F0] A", 0.91);
        SearchResultResponse second = canonicalResult(902L, "[F0] B", 0.89);
        SearchResultResponse third = canonicalResult(903L, "[F0] C", 0.87);
        Document evidenceA = evidenceDocument(12L, "Evidence A");
        Document evidenceB = evidenceDocument(14L, "Evidence B");
        List<EvaluationTrace.Candidate> ranking = EvaluationTrace.Builder.projectEvidence(
                List.of(first, second, third), List.of(
                        evidenceRef(1L, 901L, 0, evidenceA, null, "A"),
                        evidenceRef(2L, 902L, 0, evidenceA, null, "A again"),
                        evidenceRef(3L, 903L, 0, evidenceB, null, "B")));

        assertEquals(List.of(12L, 14L), ranking.stream().map(EvaluationTrace.Candidate::documentId).toList());
        assertEquals(List.of(1, 2), ranking.stream().map(EvaluationTrace.Candidate::rank).toList());
    }

    @Test
    void shouldApproveRelevantEvidenceAcrossMultipleCanonicalCandidates() {
        String question = "转软件工程机考要做什么准备？";
        SearchResultResponse requirements = canonicalResult(21L,
                "[F0] 申请软件工程专业转专业，需要完成微积分 I 和微积分 II。", 0.82);
        requirements.setDocumentId(6L);
        requirements.setSection("申请要求");
        SearchResultResponse examPrep = canonicalResult(456L,
                "[F0] 机试满分100分，达到60分及以上方可进入面试。\n"
                        + "[F1] 机试使用SEECODER，编程语言为Java，时长120分钟，共5道题。\n"
                        + "[F2] 作者建议练习基础数据结构与算法，并同步学习Java与算法。", 0.81);
        examPrep.setDocumentId(22L);
        examPrep.setSection("机试考核与准备");

        Document requirementsEvidence = evidenceDocument(4L, "2026转软件工程申请要求");
        requirementsEvidence.setSourceType("PERSONAL");
        Document examEvidence = evidenceDocument(2L, "2026转软件工程机考准备");
        examEvidence.setSourceType("PERSONAL");

        when(retrievalService.prepareCanonicalFirst(question)).thenReturn(preparedQuery);
        when(retrievalService.searchCanonical(preparedQuery, 3))
                .thenReturn(List.of(requirements, examPrep));
        when(evidenceRefRepository
                .findByCanonicalChunk_IdInOrderByCanonicalChunk_IdAscFactIndexAscIdAsc(
                        List.of(21L, 456L)))
                .thenReturn(List.of(
                        evidenceRef(601L, 21L, 0, requirementsEvidence, null,
                                "需要完成微积分 I 和微积分 II"),
                        evidenceRef(602L, 456L, 0, examEvidence, null,
                                "机试满分100分，达到60分及以上方可进入面试"),
                        evidenceRef(603L, 456L, 1, examEvidence, null,
                                "SEECODER；Java；120分钟；5道题"),
                        evidenceRef(604L, 456L, 2, examEvidence, null,
                                "作者建议练习基础数据结构与算法，并同步学习Java与算法")
                ));
        when(answerabilityService.check(anyString(), anyString(), anyList()))
                .thenReturn(new AnswerabilityResult(true, List.of("S2"), "机考证据充分"));
        when(chatModel.call(any(Prompt.class)))
                .thenReturn(chatResponse("机试准备建议。[S2]"));

        RagService.EvaluationTraceResult traced = ragService.askForEvaluation(question, true);
        RagResponse response = traced.response();

        assertEquals("机试准备建议。[S2]", response.getAnswer());
        assertEquals(1, response.getSources().size());
        assertEquals(2L, response.getSources().getFirst().getDocumentId());
        // documentId identifies the cited Evidence; chunkId retains the retrieved Canonical chunk.
        assertEquals(456L, response.getSources().getFirst().getChunkId());
        assertEquals("PERSONAL", response.getSources().getFirst().getSourceType());
        assertEquals("S2", response.getSources().getFirst().getCitationId());
        verify(retrievalService, never()).searchEvidence(any(), any(Integer.class));

        ArgumentCaptor<Prompt> generationPrompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(generationPrompt.capture());
        String generationContext = generationPrompt.getValue().getUserMessage().getText();
        assertFalse(generationContext.contains("微积分 I"));
        assertTrue(generationContext.contains("SEECODER"));
        assertTrue(generationContext.contains("基础数据结构与算法"));
        assertTrue(generationContext.contains("documentId: 2\nsourceType: PERSONAL\nofficial: false"));
        assertFalse(generationContext.contains("documentId: 4\n"));
        assertFalse(generationContext.contains("[S1]"));
        assertEquals(3, generationContext.split("supportedBy: \\[S2]", -1).length - 1);

        assertTrue(generationPrompt.getValue().getSystemMessage().getText()
                .contains("不得将 PERSONAL 信息表述为官方规定"));

        ArgumentCaptor<String> context = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<List<SourceResponse>> sources = ArgumentCaptor.forClass(List.class);
        verify(answerabilityService).check(anyString(), context.capture(), sources.capture());
        assertTrue(context.getValue().contains("[F0] 申请软件工程专业"));
        assertTrue(context.getValue().contains("[F2] 作者建议"));
        assertEquals(List.of("S1", "S2"), sources.getValue().stream()
                .map(SourceResponse::getCitationId).toList());
        assertEquals(List.of(4L, 2L), sources.getValue().stream()
                .map(SourceResponse::getDocumentId).toList());
        assertEquals(3, context.getValue().split("Evidence: \\[S2]", -1).length - 1);
        assertEquals(2, traced.trace().canonicalRanking().size());
        assertTrue(traced.trace().retrievedContexts().getFirst().contains("[F0] 申请软件工程专业"));
        assertTrue(traced.trace().retrievedContexts().getFirst().contains("[F2] 作者建议"));
        assertFalse(traced.trace().approvedContexts().getFirst().contains("[F0] 申请软件工程专业"));
        assertTrue(traced.trace().approvedContexts().getFirst().contains("作者建议练习基础数据结构与算法"));
    }

    @Test
    void shouldFallbackWhenCanonicalAnswerabilityProvidesNoApprovedEvidenceIds() {
        String question = "问题";
        SearchResultResponse canonical = canonicalResult(900L, "[F0] 事实", 0.80);
        Document evidence = evidenceDocument(12L, "通知");
        when(retrievalService.prepareCanonicalFirst(question)).thenReturn(preparedQuery);
        when(retrievalService.searchCanonical(preparedQuery, 3)).thenReturn(List.of(canonical));
        when(evidenceRefRepository
                .findByCanonicalChunk_IdInOrderByCanonicalChunk_IdAscFactIndexAscIdAsc(List.of(900L)))
                .thenReturn(List.of(evidenceRef(1L, 900L, 0, evidence, null, "直接证据")));
        when(answerabilityService.check(anyString(), anyString(), anyList()))
                .thenReturn(new AnswerabilityResult(true, List.of(), "缺少引用"));
        when(retrievalService.searchEvidence(preparedQuery, 3)).thenReturn(List.of());

        RagResponse response = ragService.ask(question);

        assertEquals("根据当前知识库资料无法确定。", response.getAnswer());
        assertTrue(response.getSources().isEmpty());
        verify(retrievalService).searchEvidence(preparedQuery, 3);
        verify(chatModel, never()).call(any(Prompt.class));
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

        RagService.EvaluationTraceResult traced = ragService.askForEvaluation(question, true);
        RagResponse response = traced.response();

        assertEquals("答案。[S1]", response.getAnswer());
        assertEquals(12L, response.getSources().getFirst().getDocumentId());
        assertEquals("EVIDENCE", traced.trace().retrievalLayer());
        assertTrue(traced.trace().fallback());
        assertEquals("NO_CANONICAL_RESULT", traced.trace().fallbackReason());
        assertEquals(List.of(120L), traced.trace().evidenceFallbackRanking().stream()
                .map(EvaluationTrace.Candidate::chunkId).toList());
        assertTrue(traced.trace().retrievedContexts().getFirst().contains("原始资料正文"));
        assertTrue(traced.trace().approvedContexts().getFirst().contains("原始资料正文"));
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
    void shouldAnswerFromEvidenceAfterCanonicalAnswerabilityFails() {
        String question = "转软件工程机考怎么准备？";
        SearchResultResponse canonical = canonicalResult(900L, "[F0] 申请课程要求", 0.80);
        SearchResultResponse evidenceResult = new SearchResultResponse();
        evidenceResult.setDocumentId(12L);
        evidenceResult.setChunkId(120L);
        evidenceResult.setChunkIndex(0);
        evidenceResult.setContent("机考准备经验");
        evidenceResult.setScore(0.75);
        Document evidence = evidenceDocument(12L, "机考经验");
        when(retrievalService.prepareCanonicalFirst(question)).thenReturn(preparedQuery);
        when(retrievalService.searchCanonical(preparedQuery, 3)).thenReturn(List.of(canonical));
        when(evidenceRefRepository
                .findByCanonicalChunk_IdInOrderByCanonicalChunk_IdAscFactIndexAscIdAsc(List.of(900L)))
                .thenReturn(List.of(evidenceRef(1L, 900L, 0, evidence, null, "课程依据")));
        when(answerabilityService.check(anyString(), anyString(), anyList()))
                .thenReturn(AnswerabilityResult.notAnswerable("机考问题与课程事实不符"))
                .thenReturn(new AnswerabilityResult(true, List.of("S1"), "机考证据充分"));
        when(retrievalService.searchEvidence(preparedQuery, 3)).thenReturn(List.of(evidenceResult));
        when(documentRepository.findAllById(java.util.Set.of(12L))).thenReturn(List.of(evidence));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("依据经验准备机考。[S1]"));

        RagResponse response = ragService.ask(question);

        assertEquals("依据经验准备机考。[S1]", response.getAnswer());
        assertEquals(12L, response.getSources().getFirst().getDocumentId());
        verify(retrievalService).searchEvidence(preparedQuery, 3);
        verify(answerabilityService, times(2)).check(anyString(), anyString(), anyList());
    }

    @Test
    void shouldFailClosedAfterBothCanonicalAndEvidenceAreInsufficient() {
        String question = "转入后保证国家奖学金吗？";
        SearchResultResponse canonical = canonicalResult(900L, "[F0] 申请课程要求", 0.80);
        SearchResultResponse evidenceResult = new SearchResultResponse();
        evidenceResult.setDocumentId(12L);
        evidenceResult.setChunkId(120L);
        evidenceResult.setChunkIndex(0);
        evidenceResult.setContent("申请课程资料");
        evidenceResult.setScore(0.75);
        Document evidence = evidenceDocument(12L, "申请资料");
        when(retrievalService.prepareCanonicalFirst(question)).thenReturn(preparedQuery);
        when(retrievalService.searchCanonical(preparedQuery, 3)).thenReturn(List.of(canonical));
        when(evidenceRefRepository
                .findByCanonicalChunk_IdInOrderByCanonicalChunk_IdAscFactIndexAscIdAsc(List.of(900L)))
                .thenReturn(List.of(evidenceRef(1L, 900L, 0, evidence, null, "课程依据")));
        when(answerabilityService.check(anyString(), anyString(), anyList()))
                .thenReturn(AnswerabilityResult.notAnswerable("Canonical 不足"))
                .thenReturn(AnswerabilityResult.notAnswerable("Evidence 不足"));
        when(retrievalService.searchEvidence(preparedQuery, 3)).thenReturn(List.of(evidenceResult));
        when(documentRepository.findAllById(java.util.Set.of(12L))).thenReturn(List.of(evidence));

        RagResponse response = ragService.ask(question);

        assertEquals("根据当前知识库资料无法确定。", response.getAnswer());
        assertTrue(response.getSources().isEmpty());
        verify(retrievalService).searchEvidence(preparedQuery, 3);
        verify(answerabilityService, times(2)).check(anyString(), anyString(), anyList());
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
        assertTrue(answerabilityContext.getValue().contains("同页重复"));
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        assertFalse(prompt.getValue().getUserMessage().getText().contains("[F0]"));
    }

    @Test
    void shouldKeepThreeFactExcerptsForOneDocumentWithoutPageAndReturnOneCitation() {
        String question = "转软件工程申请需要修哪些课程？";
        SearchResultResponse canonical = productionLikeCanonical();
        Document evidence = evidenceDocument(4L, "2026转软件工程申请要求");
        evidence.setSourceType("PERSONAL");
        when(retrievalService.prepareCanonicalFirst(question)).thenReturn(preparedQuery);
        when(retrievalService.searchCanonical(preparedQuery, 3)).thenReturn(List.of(canonical));
        when(evidenceRefRepository
                .findByCanonicalChunk_IdInOrderByCanonicalChunk_IdAscFactIndexAscIdAsc(List.of(21L)))
                .thenReturn(productionLikeRefs(evidence));
        AnswerabilityService realAnswerability = new AnswerabilityService(chatModel);
        RagService service = new RagService(retrievalService, realAnswerability, chatModel,
                documentRepository, evidenceRefRepository, true);
        when(chatModel.call(any(Prompt.class))).thenReturn(
                chatResponse("{\"answerable\":true,\"evidenceCitationIds\":[\"S1\"],\"reason\":\"三条事实均有证据\"}"),
                chatResponse("需要完成微积分 I 和 II；大二申请还需线性代数；专业准入课程需已修或在修。[S1]"));

        RagResponse response = service.ask(question);

        assertEquals(1, response.getSources().size());
        assertEquals(4L, response.getSources().getFirst().getDocumentId());
        assertEquals("S1", response.getSources().getFirst().getCitationId());
        assertEquals("PERSONAL", response.getSources().getFirst().getSourceType());
        assertEquals(null, response.getSources().getFirst().getSourcePage());
        verify(retrievalService, never()).searchEvidence(any(), any(Integer.class));
        ArgumentCaptor<Prompt> prompts = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel, times(2)).call(prompts.capture());
        String checkerContext = prompts.getAllValues().getFirst().getUserMessage().getText();
        for (int index = 0; index < 3; index++) {
            assertTrue(checkerContext.contains("[F" + index + "]"));
        }
        assertEquals(3, checkerContext.split("Evidence: \\[S1]", -1).length - 1);
        assertTrue(checkerContext.contains("微积分 I（第一层次）"));
        assertTrue(checkerContext.contains("线性代数（第一层次）"));
        assertTrue(checkerContext.contains("离散数学"));
        String generationContext = prompts.getAllValues().get(1).getUserMessage().getText();
        assertFalse(generationContext.contains("[F0]"));
        assertTrue(generationContext.contains("线性代数（第一层次）"));
        assertTrue(generationContext.contains("离散数学"));
    }

    @Test
    void shouldFallbackWhenProductionLikeFactF2HasNoEvidenceRef() {
        String question = "转软件工程申请需要修哪些课程？";
        Document evidence = evidenceDocument(4L, "2026转软件工程申请要求");
        when(retrievalService.prepareCanonicalFirst(question)).thenReturn(preparedQuery);
        when(retrievalService.searchCanonical(preparedQuery, 3)).thenReturn(List.of(productionLikeCanonical()));
        when(evidenceRefRepository
                .findByCanonicalChunk_IdInOrderByCanonicalChunk_IdAscFactIndexAscIdAsc(List.of(21L)))
                .thenReturn(productionLikeRefs(evidence).subList(0, 2));
        when(retrievalService.searchEvidence(preparedQuery, 3)).thenReturn(List.of());

        ragService.ask(question);

        verify(answerabilityService, never()).check(anyString(), anyString(), anyList());
        verify(retrievalService).searchEvidence(preparedQuery, 3);
    }

    @Test
    void shouldFallbackWhenModelCitesUnknownSourceForProductionLikeFacts() {
        String question = "转软件工程申请需要修哪些课程？";
        Document evidence = evidenceDocument(4L, "2026转软件工程申请要求");
        when(retrievalService.prepareCanonicalFirst(question)).thenReturn(preparedQuery);
        when(retrievalService.searchCanonical(preparedQuery, 3)).thenReturn(List.of(productionLikeCanonical()));
        when(evidenceRefRepository
                .findByCanonicalChunk_IdInOrderByCanonicalChunk_IdAscFactIndexAscIdAsc(List.of(21L)))
                .thenReturn(productionLikeRefs(evidence));
        when(retrievalService.searchEvidence(preparedQuery, 3)).thenReturn(List.of());
        AnswerabilityService realAnswerability = new AnswerabilityService(chatModel);
        RagService service = new RagService(retrievalService, realAnswerability, chatModel,
                documentRepository, evidenceRefRepository, true);
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(
                "{\"answerable\":true,\"evidenceCitationIds\":[\"S99\"],\"reason\":\"错误引用\"}"));

        RagResponse response = service.ask(question);

        assertEquals("根据当前知识库资料无法确定。", response.getAnswer());
        verify(retrievalService).searchEvidence(preparedQuery, 3);
        verify(chatModel, times(1)).call(any(Prompt.class));
    }

    @Test
    void shouldFallbackWhenReferencedEvidenceDocumentIsMissing() {
        String question = "转软件工程申请需要修哪些课程？";
        Document evidence = evidenceDocument(4L, "2026转软件工程申请要求");
        List<EvidenceRef> refs = new java.util.ArrayList<>(productionLikeRefs(evidence));
        refs.get(2).setEvidenceDocument(null);
        when(retrievalService.prepareCanonicalFirst(question)).thenReturn(preparedQuery);
        when(retrievalService.searchCanonical(preparedQuery, 3)).thenReturn(List.of(productionLikeCanonical()));
        when(evidenceRefRepository
                .findByCanonicalChunk_IdInOrderByCanonicalChunk_IdAscFactIndexAscIdAsc(List.of(21L)))
                .thenReturn(refs);
        when(retrievalService.searchEvidence(preparedQuery, 3)).thenReturn(List.of());

        ragService.ask(question);

        verify(answerabilityService, never()).check(anyString(), anyString(), anyList());
        verify(retrievalService).searchEvidence(preparedQuery, 3);
    }

    @Test
    void shouldPreservePersonalAuthorityWithoutInventingPolicyYear() {
        SearchResultResponse canonical = canonicalResult(456L, "[F0] 资料作者建议练习数组与链表。", 0.80);
        canonical.setDocumentId(22L);
        canonical.setPolicyYear(null);
        Document evidence = evidenceDocument(2L, "2026转软件工程机考准备");
        evidence.setSourceType("PERSONAL");

        Prompt prompt = generateCanonicalPrompt(List.of(canonical),
                List.of(evidenceRef(1L, 456L, 0, evidence, null, "作者建议练习数组与链表")),
                List.of("S1"));

        String context = prompt.getUserMessage().getText();
        assertTrue(context.contains("[S1]\ndocumentId: 2\nsourceType: PERSONAL\nofficial: false"));
        assertTrue(context.contains("title: 2026转软件工程机考准备"));
        assertTrue(context.contains("documentYear: 2026\npolicyYear: 未知\ncohortYear: 未知"));
        assertTrue(context.contains("supportedBy: [S1]\n资料作者建议练习数组与链表。"));
        assertFalse(context.contains("sourceType: OFFICIAL_PDF"));
        String rules = prompt.getSystemMessage().getText();
        assertTrue(rules.contains("不得将 PERSONAL 信息表述为官方规定"));
        assertTrue(rules.contains("根据个人整理资料"));
        assertTrue(rules.contains("不得将 documentYear 当作 policyYear 或 cohortYear"));
    }

    @Test
    void shouldPreserveOfficialAuthorityAndYearConstraints() {
        SearchResultResponse canonical = canonicalResult(900L, "[F0] 复试包括机试和面试。", 0.80);
        canonical.setCohortYear(2024);
        Document evidence = evidenceDocument(20L, "南京大学2026年转专业准入计划表");

        Prompt prompt = generateCanonicalPrompt(List.of(canonical),
                List.of(evidenceRef(1L, 900L, 0, evidence, 3, "复试包括机试和面试")),
                List.of("S1"));

        String context = prompt.getUserMessage().getText();
        assertTrue(context.contains("documentId: 20\nsourceType: OFFICIAL_PDF\nofficial: true"));
        assertTrue(context.contains("title: 南京大学2026年转专业准入计划表"));
        assertTrue(context.contains("policyYear: 2026\ncohortYear: 2024"));
        assertFalse(context.contains("sourceType: PERSONAL"));
        assertTrue(prompt.getSystemMessage().getText().contains("除非 Evidence 明确支持"));
    }

    @Test
    void shouldKeepMixedSourceAuthorityLinkedToEachApprovedFact() {
        SearchResultResponse official = canonicalResult(900L, "[F0] 复试包括机试和面试。", 0.80);
        official.setCohortYear(2024);
        SearchResultResponse personal = canonicalResult(901L, "[F0] 作者建议练习数组与链表。", 0.79);
        personal.setPolicyYear(null);
        Document officialEvidence = evidenceDocument(20L, "2026年官方准入计划表");
        Document personalEvidence = evidenceDocument(2L, "2026转软件工程机考准备");
        personalEvidence.setSourceType("PERSONAL");

        Prompt prompt = generateCanonicalPrompt(List.of(official, personal), List.of(
                evidenceRef(1L, 900L, 0, officialEvidence, 3, "复试包括机试和面试"),
                evidenceRef(2L, 901L, 0, personalEvidence, null, "作者建议练习数组与链表")),
                List.of("S1", "S2"));

        String context = prompt.getUserMessage().getText();
        assertTrue(context.contains("supportedBy: [S1]\n复试包括机试和面试。"));
        assertTrue(context.contains("supportedBy: [S2]\n作者建议练习数组与链表。"));
        String sourceContext = context.substring(context.indexOf("已通过证据充分性检查的 Sources："));
        String officialBlock = sourceContext.substring(sourceContext.indexOf("[S1]"),
                sourceContext.indexOf("[S2]"));
        String personalBlock = sourceContext.substring(sourceContext.indexOf("[S2]"));
        assertTrue(officialBlock.contains("documentId: 20\nsourceType: OFFICIAL_PDF\nofficial: true"));
        assertTrue(officialBlock.contains("policyYear: 2026\ncohortYear: 2024"));
        assertFalse(officialBlock.contains("sourceType: PERSONAL"));
        assertTrue(personalBlock.contains("documentId: 2\nsourceType: PERSONAL\nofficial: false"));
        assertTrue(personalBlock.contains("policyYear: 未知\ncohortYear: 未知"));
        assertFalse(personalBlock.contains("official: true"));
        assertTrue(prompt.getSystemMessage().getText()
                .contains("存在官方资料不能让 PERSONAL facts 一并成为官方信息"));
    }

    @Test
    void shouldOmitUnapprovedAuthorityLinkFromPartiallyApprovedFact() {
        SearchResultResponse canonical = canonicalResult(900L, "[F0] 作者建议练习数组。", 0.80);
        Document official = evidenceDocument(20L, "官方资料");
        Document personal = evidenceDocument(2L, "经验资料");
        personal.setSourceType("PERSONAL");

        Prompt prompt = generateCanonicalPrompt(List.of(canonical), List.of(
                evidenceRef(1L, 900L, 0, official, null, "证据 A"),
                evidenceRef(2L, 900L, 0, personal, null, "证据 B")), List.of("S2"));

        String context = prompt.getUserMessage().getText();
        assertTrue(context.contains("supportedBy: [S2]\n作者建议练习数组。"));
        assertTrue(context.contains("documentId: 2\nsourceType: PERSONAL\nofficial: false"));
        assertFalse(context.contains("[S1]"));
        assertFalse(context.contains("documentId: 20\n"));
        assertFalse(context.contains("证据 A"));
    }

    @Test
    void shouldPreserveFactSpecificYearsWhenEvidenceCitationIsDeduplicated() {
        SearchResultResponse historical = canonicalResult(900L, "[F0] 历史要求。", 0.80);
        historical.setSection("历史要求");
        historical.setPolicyYear(2025);
        historical.setCohortYear(2023);
        SearchResultResponse current = canonicalResult(901L, "[F0] 当前要求。", 0.79);
        current.setSection("当前要求");
        current.setCohortYear(2024);
        Document evidence = evidenceDocument(20L, "历年计划汇总");

        Prompt prompt = generateCanonicalPrompt(List.of(historical, current), List.of(
                evidenceRef(1L, 900L, 0, evidence, null, "2025年适用于2023级的要求"),
                evidenceRef(2L, 901L, 0, evidence, null, "2026年适用于2024级的要求")), List.of("S1"));

        String context = prompt.getUserMessage().getText();
        assertTrue(context.contains("section: 历史要求\npolicyYear: 2025\ncohortYear: 2023\nsupportedBy: [S1]"));
        assertTrue(context.contains("section: 当前要求\npolicyYear: 2026\ncohortYear: 2024\nsupportedBy: [S1]"));
        assertEquals(1, context.split("documentId: 20\\n", -1).length - 1);
        assertTrue(prompt.getSystemMessage().getText().contains("按每条 fact 自身的年份限定表述"));
    }

    private Prompt generateCanonicalPrompt(List<SearchResultResponse> candidates,
                                           List<EvidenceRef> refs, List<String> approvedIds) {
        String question = "来源权威性测试";
        when(retrievalService.prepareCanonicalFirst(question)).thenReturn(preparedQuery);
        when(retrievalService.searchCanonical(preparedQuery, 3)).thenReturn(candidates);
        when(evidenceRefRepository.findByCanonicalChunk_IdInOrderByCanonicalChunk_IdAscFactIndexAscIdAsc(
                candidates.stream().map(SearchResultResponse::getChunkId).toList())).thenReturn(refs);
        when(answerabilityService.check(anyString(), anyString(), anyList()))
                .thenReturn(new AnswerabilityResult(true, approvedIds, "充分"));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("模型原始回答"));

        RagResponse response = ragService.ask(question);

        assertEquals("模型原始回答", response.getAnswer());
        verify(retrievalService, never()).searchEvidence(any(), any(Integer.class));
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(prompt.capture());
        return prompt.getValue();
    }

    private SearchResultResponse productionLikeCanonical() {
        SearchResultResponse result = canonicalResult(21L, "[F0] 申请软件工程专业转专业，需要完成微积分 I（第一层次）和微积分 II（第一层次）。\n"
                + "[F1] 若申请时为大二学生，还需完成线性代数（第一层次）。\n"
                + "[F2] 申请前需已修或在修 C语言程序设计基础（CPL）、计算系统基础 I、离散数学、软件工程与计算 I。", 0.80);
        result.setSection("申请要求");
        result.setDocumentId(6L);
        result.setMajor("软件工程");
        result.setPolicyYear(null);
        return result;
    }

    private List<EvidenceRef> productionLikeRefs(Document evidence) {
        return List.of(
                evidenceRef(1L, 21L, 0, evidence, null,
                        "申请软件工程专业转专业，需要完成以下申请流程：\n- 微积分 I（第一层次）\n- 微积分 II（第一层次）"),
                evidenceRef(2L, 21L, 1, evidence, null,
                        "若申请时为大二学生，还需完成：\n- 线性代数（第一层次）"),
                evidenceRef(3L, 21L, 2, evidence, null,
                        "申请前需已修或在修以下专业准入课程：\n- C语言程序设计基础（CPL）\n- 计算系统基础 I\n- 离散数学\n- 软件工程与计算 I"));
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
