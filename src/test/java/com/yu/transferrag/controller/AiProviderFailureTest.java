package com.yu.transferrag.controller;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.openai.core.http.Headers;
import com.openai.errors.*;
import com.yu.transferrag.dto.SearchResultResponse;
import com.yu.transferrag.entity.Document;
import com.yu.transferrag.exception.AiServiceUnavailableException;
import com.yu.transferrag.repository.DocumentRepository;
import com.yu.transferrag.repository.EvidenceRefRepository;
import com.yu.transferrag.service.*;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AiProviderFailureTest {
    private RetrievalService retrieval;
    private ChatModel chat;
    private DocumentRepository documents;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        retrieval = mock(RetrievalService.class);
        chat = mock(ChatModel.class);
        documents = mock(DocumentRepository.class);
        useService(false);
    }

    private void useService(boolean canonical) {
        RagService service = new RagService(retrieval, new AnswerabilityService(chat), chat,
                documents, mock(EvidenceRefRepository.class), canonical);
        mvc = MockMvcBuilders.standaloneSetup(new RagController(service))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void canonicalEmbeddingFailureReturnsSafe503AndStopsBeforeSearch() throws Exception {
        useService(true);
        EmbeddingModel embedding = mock(EmbeddingModel.class);
        when(embedding.embed(anyString())).thenThrow(notFound());
        var adapter = new QdrantPrecomputedVectorSearch(null, embedding, "transfer_chunks");
        when(retrieval.prepareCanonicalFirst(anyString())).thenAnswer(invocation -> {
            adapter.embed(invocation.getArgument(0));
            return null;
        });
        assertSafe503();
        verify(retrieval, never()).searchCanonical(any(), anyInt());
        verifyNoInteractions(chat);
        verify(embedding, times(1)).embed(anyString());
    }

    @Test
    void legacyVectorStoreEmbeddingFailureHasEmbeddingStage() {
        VectorStore store = mock(VectorStore.class);
        when(store.similaritySearch(any(org.springframework.ai.vectorstore.SearchRequest.class)))
                .thenThrow(notFound());
        var service = new RetrievalService(store, new QueryRewriteService(
                mock(com.yu.transferrag.repository.EntityAliasRepository.class)),
                mock(com.yu.transferrag.repository.ChunkRepository.class), null);
        var exception = assertThrows(AiServiceUnavailableException.class,
                () -> service.search("问题", 3));
        assertEquals(AiServiceUnavailableException.Stage.EMBEDDING, exception.getStage());
    }

    @Test
    void answerabilityFailureReturns503InsteadOfKnowledgeFallback() throws Exception {
        relevantEvidence();
        when(chat.call(any(Prompt.class))).thenThrow(notFound());
        assertSafe503();
        verify(chat, times(1)).call(any(Prompt.class));
    }

    @Test
    void generationFailureReturns503() throws Exception {
        relevantEvidence();
        when(chat.call(any(Prompt.class)))
                .thenReturn(response("{\"answerable\":true,\"evidenceCitationIds\":[\"S1\"],\"reason\":\"supported\"}"))
                .thenThrow(notFound());
        assertSafe503();
        verify(chat, times(2)).call(any(Prompt.class));
    }

    @Test
    void noEvidenceRemains200WithoutCallingProvider() throws Exception {
        when(retrieval.search(anyString(), anyInt())).thenReturn(List.of());
        ask().andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("根据当前知识库资料无法确定。"));
        verifyNoInteractions(chat);
    }

    @Test
    void notAnswerableRemains200() throws Exception {
        relevantEvidence();
        when(chat.call(any(Prompt.class))).thenReturn(response(
                "{\"answerable\":false,\"evidenceCitationIds\":[],\"reason\":\"insufficient\"}"));
        ask().andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("根据当前知识库资料无法确定。"));
    }

    @Test
    void invalidAnswerabilityOutputStillFailsClosed() throws Exception {
        relevantEvidence();
        when(chat.call(any(Prompt.class))).thenReturn(response("not valid JSON"));
        ask().andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("根据当前知识库资料无法确定。"));
    }

    @Test
    void unrelatedRuntimeBugIsNotHandledAs503() {
        IllegalStateException bug = new IllegalStateException("internal bug");
        when(retrieval.search(anyString(), anyInt())).thenThrow(bug);
        ServletException exception = assertThrows(ServletException.class, this::ask);
        assertSame(bug, exception.getCause());
        assertSame(bug, assertThrows(IllegalStateException.class,
                () -> AiServiceUnavailableException.call(
                        AiServiceUnavailableException.Stage.EMBEDDING, () -> { throw bug; })));
    }

    @Test
    void existingBootErrorDispatchStillReturns500ForInternalBug() throws Exception {
        var errorController = new org.springframework.boot.webmvc.autoconfigure.error.BasicErrorController(
                new org.springframework.boot.webmvc.error.DefaultErrorAttributes(),
                new org.springframework.boot.autoconfigure.web.ErrorProperties());
        var errors = MockMvcBuilders.standaloneSetup(errorController)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        errors.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get("/error")
                        .accept("application/json")
                        .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_STATUS_CODE, 500)
                        .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_REQUEST_URI, "/api/rag/ask")
                        .requestAttr(jakarta.servlet.RequestDispatcher.ERROR_EXCEPTION,
                                new IllegalStateException("internal bug")))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.status").value(500))
                .andExpect(jsonPath("$.code").doesNotExist());
    }

    @Test
    void parameterValidationStillReturns400() throws Exception {
        mvc.perform(post("/api/rag/ask").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(retrieval, chat);
    }

    static Stream<RuntimeException> providerFailures() {
        Headers headers = Headers.builder().build();
        return Stream.of(notFound(), RateLimitException.builder().headers(headers).build(),
                InternalServerException.builder().statusCode(500).headers(headers).build(),
                new OpenAIIoException("sensitive upstream message", new SocketTimeoutException()),
                new OpenAIIoException("sensitive upstream message", new IOException()));
    }

    @ParameterizedTest
    @MethodSource("providerFailures")
    void mapsProviderHttpAndTransportFailuresWithoutRetry(RuntimeException failure) throws Exception {
        @SuppressWarnings("unchecked")
        java.util.function.Supplier<Object> operation = mock(java.util.function.Supplier.class);
        when(operation.get()).thenThrow(failure);
        var exception = assertThrows(AiServiceUnavailableException.class,
                () -> AiServiceUnavailableException.call(AiServiceUnavailableException.Stage.EMBEDDING, operation));
        assertSame(failure, exception.getCause());
        Integer expectedStatus = failure instanceof OpenAIServiceException service ? service.statusCode() : null;
        assertEquals(expectedStatus, exception.getUpstreamStatus());
        when(retrieval.search(anyString(), anyInt())).thenThrow(exception);
        assertSafe503();
        verify(operation, times(1)).get();
    }

    @Test
    void logsOnlyStageClassAndStatusNotProviderDetails() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            var failure = new OpenAIIoException("sk-secret https://provider.invalid bge-m3 Authorization query prompt evidence answer");
            var wrapped = assertThrows(AiServiceUnavailableException.class,
                    () -> AiServiceUnavailableException.call(AiServiceUnavailableException.Stage.GENERATION,
                            () -> { throw failure; }));
            when(retrieval.search(anyString(), anyInt())).thenThrow(wrapped);
            assertSafe503();
            assertEquals(1, appender.list.size());
            assertEquals("AI provider unavailable: stage=GENERATION status=null exception=OpenAIIoException",
                    appender.list.getFirst().getFormattedMessage());
            assertNull(appender.list.getFirst().getThrowableProxy());
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }
    }

    private static NotFoundException notFound() {
        return NotFoundException.builder().headers(Headers.builder().build()).build();
    }

    private void relevantEvidence() {
        var result = new SearchResultResponse();
        result.setDocumentId(2L);
        result.setChunkId(456L);
        result.setScore(0.9);
        result.setContent("evidence");
        when(retrieval.search(anyString(), anyInt())).thenReturn(List.of(result));
        Document document = new Document();
        document.setId(2L);
        document.setTitle("资料");
        document.setSourceType("PERSONAL");
        when(documents.findAllById(any())).thenReturn(List.of(document));
    }

    private ChatResponse response(String text) {
        return new ChatResponse(List.of(new Generation(new AssistantMessage(text))));
    }

    private org.springframework.test.web.servlet.ResultActions ask() throws Exception {
        return mvc.perform(post("/api/rag/ask").contentType("application/json")
                .content("{\"question\":\"问题\"}"));
    }

    private void assertSafe503() throws Exception {
        String body = ask().andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.path").value("/api/rag/ask"))
                .andExpect(jsonPath("$.code").value("AI_SERVICE_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("AI 服务暂时不可用，请稍后重试。"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        for (String forbidden : List.of("bge-m3", "deepseek", "https://", "sk-secret", "NotFoundException",
                "OpenAIIoException", "Authorization", "trace", "prompt", "evidence")) {
            assertFalse(body.contains(forbidden), forbidden);
        }
    }
}
