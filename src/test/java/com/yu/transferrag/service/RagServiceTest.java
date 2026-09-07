package com.yu.transferrag.service;

import com.yu.transferrag.dto.AnswerabilityResult;
import com.yu.transferrag.dto.RagResponse;
import com.yu.transferrag.dto.SearchResultResponse;
import com.yu.transferrag.dto.SourceResponse;
import com.yu.transferrag.entity.Document;
import com.yu.transferrag.repository.DocumentRepository;
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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagServiceTest {

    @Mock
    private RetrievalService retrievalService;

    @Mock
    private ChatModel chatModel;

    @Mock
    private AnswerabilityService answerabilityService;

    @Mock
    private DocumentRepository documentRepository;

    private RagService ragService;

    @BeforeEach
    void setUp() {
        ragService = new RagService(
                retrievalService,
                answerabilityService,
                chatModel,
                documentRepository
        );
    }

    @Test
    void shouldBuildStableCitationsAndCompleteSourcesWithOneBatchDocumentQuery() {
        String question = "2025年汉语言文学转专业需要什么条件？";
        List<SearchResultResponse> results = List.of(
                searchResult(6, 116, 0, 0.677, "文学院", "汉语言文学", 2025, 2025),
                searchResult(20, 201, 1, 0.620, "文学院", "汉语言文学", 2025, 2025),
                searchResult(6, 117, 2, 0.600, "文学院", "汉语言文学", 2025, 2025)
        );
        Document official = document(
                6,
                "南京大学2026年全日制本科生跨大类专业准入计划",
                "本科生院",
                2026,
                "OFFICIAL_PDF",
                "GLOBAL"
        );
        Document personal = document(
                20,
                "2025汉语言文学转专业指南",
                "文学院",
                2025,
                "PERSONAL",
                "DEPARTMENT"
        );
        when(retrievalService.search(question, 3)).thenReturn(results);
        when(documentRepository.findAllById(Set.of(6L, 20L)))
                .thenReturn(List.of(official, personal));
        when(answerabilityService.check(anyString(), anyString(), anyList()))
                .thenReturn(new AnswerabilityResult(
                        true,
                        List.of("S1", "S2", "S3"),
                        "Sources contain the requested requirements."
                ));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(
                "官方要求见准入计划。[S1] 经验资料另有备考建议。[S2]"
        ));

        RagResponse response = ragService.ask(question);

        assertEquals(question, response.getQuestion());
        assertEquals("官方要求见准入计划。[S1] 经验资料另有备考建议。[S2]", response.getAnswer());
        assertEquals(List.of("S1", "S2", "S3"), response.getSources().stream()
                .map(SourceResponse::getCitationId)
                .toList());

        SourceResponse sourceOne = response.getSources().get(0);
        assertEquals(6L, sourceOne.getDocumentId());
        assertEquals(116L, sourceOne.getChunkId());
        assertEquals(0, sourceOne.getChunkIndex());
        assertEquals("南京大学2026年全日制本科生跨大类专业准入计划", sourceOne.getTitle());
        assertEquals("OFFICIAL_PDF", sourceOne.getSourceType());
        assertTrue(sourceOne.isOfficial());
        assertEquals("本科生院", sourceOne.getDocumentDepartment());
        assertEquals(2026, sourceOne.getDocumentYear());
        assertEquals("GLOBAL", sourceOne.getScope());
        assertEquals("文学院", sourceOne.getChunkDepartment());
        assertEquals("汉语言文学", sourceOne.getMajor());
        assertEquals(2025, sourceOne.getPolicyYear());
        assertEquals(2025, sourceOne.getEffectiveYear());
        assertEquals(0.677, sourceOne.getScore());

        SourceResponse sourceTwo = response.getSources().get(1);
        assertEquals("PERSONAL", sourceTwo.getSourceType());
        assertFalse(sourceTwo.isOfficial());

        verify(documentRepository).findAllById(Set.of(6L, 20L));

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        Prompt prompt = promptCaptor.getValue();
        String systemInstruction = prompt.getSystemMessage().getText();
        String userPrompt = prompt.getUserMessage().getText();

        assertTrue(systemInstruction.contains("OFFICIAL 或 OFFICIAL_PDF"));
        assertTrue(systemInstruction.contains("官方与非官方资料冲突时采用官方资料"));
        assertTrue(systemInstruction.contains("不得编造引用"));
        assertTrue(userPrompt.indexOf("[S1]") < userPrompt.indexOf("[S2]"));
        assertTrue(userPrompt.indexOf("[S2]") < userPrompt.indexOf("[S3]"));
        assertTrue(userPrompt.contains("sourceType: OFFICIAL_PDF"));
        assertTrue(userPrompt.contains("official: true"));
        assertTrue(userPrompt.contains("sourceType: PERSONAL"));
        assertTrue(userPrompt.contains("official: false"));
        assertTrue(userPrompt.contains("documentDepartment: 本科生院"));
        assertTrue(userPrompt.contains("chunkDepartment: 文学院"));
        assertTrue(userPrompt.contains("policyYear: 2025"));
    }

    @Test
    void shouldRejectEmptyRetrievalWithoutLoadingDocumentsOrCallingChatModel() {
        String question = "食堂今天有什么菜？";
        when(retrievalService.search(question, 3)).thenReturn(List.of());

        RagResponse response = ragService.ask(question);

        assertEquals("根据当前知识库资料无法确定。", response.getAnswer());
        assertEquals(List.of(), response.getSources());
        verifyNoInteractions(documentRepository, answerabilityService, chatModel);
    }

    @Test
    void shouldRejectLowSimilarityWithoutLoadingDocumentsOrCallingChatModel() {
        String question = "食堂今天有什么菜？";
        when(retrievalService.search(question, 3)).thenReturn(List.of(
                searchResult(6, 116, 0, 0.549, "文学院", "汉语言文学", 2025, 2025)
        ));

        RagResponse response = ragService.ask(question);

        assertEquals("根据当前知识库资料无法确定。", response.getAnswer());
        assertEquals(List.of(), response.getSources());
        verify(documentRepository, never()).findAllById(any());
        verify(answerabilityService, never()).check(anyString(), anyString(), anyList());
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void shouldRejectUnanswerableEvidenceWithoutCallingFinalChatModel() {
        String question = "2025年汉语言文学笔试具体是哪一天？";
        SearchResultResponse result = searchResult(
                6, 116, 0, 0.677, "文学院", "汉语言文学", 2025, 2025
        );
        result.setContent("资格审核通过后，组织笔试和面试。");
        when(retrievalService.search(question, 3)).thenReturn(List.of(result));
        when(documentRepository.findAllById(Set.of(6L))).thenReturn(List.of(document(
                6, "2025汉语言文学准入计划", "本科生院", 2026, "OFFICIAL", "GLOBAL"
        )));
        when(answerabilityService.check(anyString(), anyString(), anyList()))
                .thenReturn(AnswerabilityResult.notAnswerable("Source does not provide a date."));

        RagResponse response = ragService.ask(question);

        assertEquals("根据当前知识库资料无法确定。", response.getAnswer());
        assertEquals(List.of(), response.getSources());
        verify(chatModel, never()).call(any(Prompt.class));
    }

    @Test
    void shouldUseOnlyEvidenceSourcesApprovedByAnswerabilityCheck() {
        String question = "2025年汉语言文学需要修哪些课程？";
        List<SearchResultResponse> results = List.of(
                searchResult(6, 116, 0, 0.677, "文学院", "汉语言文学", 2025, 2025),
                searchResult(20, 201, 1, 0.620, "文学院", "汉语言文学", 2025, 2025)
        );
        when(retrievalService.search(question, 3)).thenReturn(results);
        when(documentRepository.findAllById(Set.of(6L, 20L))).thenReturn(List.of(
                document(6, "官方准入计划", "本科生院", 2026, "OFFICIAL", "GLOBAL"),
                document(20, "个人经验", "文学院", 2025, "PERSONAL", "DEPARTMENT")
        ));
        when(answerabilityService.check(anyString(), anyString(), anyList()))
                .thenReturn(new AnswerabilityResult(
                        true,
                        List.of("S1"),
                        "S1 lists the required courses."
                ));
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse("准入课程见官方计划。[S1]"));

        RagResponse response = ragService.ask(question);

        assertEquals(1, response.getSources().size());
        assertEquals("S1", response.getSources().getFirst().getCitationId());
        assertEquals(116L, response.getSources().getFirst().getChunkId());

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        String finalPrompt = promptCaptor.getValue().getUserMessage().getText();
        assertTrue(finalPrompt.contains("[S1]"));
        assertFalse(finalPrompt.contains("[S2]"));
        assertTrue(finalPrompt.contains("来源 116 的正文"));
        assertFalse(finalPrompt.contains("来源 201 的正文"));
    }

    private SearchResultResponse searchResult(long documentId,
                                              long chunkId,
                                              int chunkIndex,
                                              double score,
                                              String chunkDepartment,
                                              String major,
                                              int policyYear,
                                              int effectiveYear) {
        SearchResultResponse result = new SearchResultResponse();
        result.setContent("来源 " + chunkId + " 的正文");
        result.setDocumentId(documentId);
        result.setChunkId(chunkId);
        result.setChunkIndex(chunkIndex);
        result.setScore(score);
        result.setChunkDepartment(chunkDepartment);
        result.setMajor(major);
        result.setPolicyYear(policyYear);
        result.setEffectiveYear(effectiveYear);
        return result;
    }

    private Document document(long id,
                              String title,
                              String department,
                              int year,
                              String sourceType,
                              String scope) {
        Document document = new Document();
        document.setId(id);
        document.setTitle(title);
        document.setDepartment(department);
        document.setYear(year);
        document.setSourceType(sourceType);
        document.setScope(scope);
        return document;
    }

    private ChatResponse chatResponse(String answer) {
        return new ChatResponse(List.of(
                new Generation(new AssistantMessage(answer))
        ));
    }
}
