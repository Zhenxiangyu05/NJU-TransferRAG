package com.yu.transferrag.conversation;

import com.yu.transferrag.controller.*;
import com.yu.transferrag.service.RagService;
import org.junit.jupiter.api.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Map;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.mockito.Mockito.*;

class ConversationHttpTest {
    ConversationService sessions;RagService rag;MockMvc mvc;
    @BeforeEach void setup(){sessions=mock(ConversationService.class);rag=mock(RagService.class);mvc=MockMvcBuilders.standaloneSetup(new RagController(rag,sessions)).setControllerAdvice(new GlobalExceptionHandler()).build();}
    @Test void legacyJsonUsesUnchangedSingleTurnPath() throws Exception {
        when(rag.ask("完整问题")).thenReturn(ConversationServiceTest.answer("完整问题"));
        mvc.perform(post("/api/rag/ask").contentType("application/json").content("{\"question\":\"完整问题\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.sources[0].documentId").value(16));verifyNoInteractions(sessions);
    }
    @Test void sessionCreateUsesExistingRateLimitedPathAndNoStoreHeader() throws Exception {
        when(sessions.newConversation(null)).thenReturn(Map.of("conversationToken","x".repeat(43),"nextRequestId","00000000-0000-0000-0000-000000000001","expiresInSeconds",1800L));
        mvc.perform(post("/api/rag/ask").contentType("application/json").content("{\"action\":\"NEW_CONVERSATION\"}"))
                .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.conversationToken").value("x".repeat(43)));verifyNoInteractions(rag);
    }
    @Test void clientHistoryOrSystemRoleCannotBeInjected() throws Exception {
        for(String field:new String[]{"history","messages","system","role"})
            mvc.perform(post("/api/rag/ask").contentType("application/json").content("{\"question\":\"x\",\""+field+"\":\"injected\"}"))
                    .andExpect(status().isBadRequest());verifyNoInteractions(sessions,rag);
    }
    @Test void clarificationIsExplicitAndNonceCanAdvanceWithoutAnotherModelAttempt() throws Exception {
        when(sessions.ask(anyString(),anyString(),anyString())).thenThrow(ConversationException.clarify("CONTEXT_AMBIGUOUS").next("new-nonce"));
        mvc.perform(post("/api/rag/ask").contentType("application/json").content("{\"question\":\"那考试呢\",\"conversationToken\":\"xxx\",\"requestId\":\"yyy\"}"))
                .andExpect(status().isUnprocessableEntity()).andExpect(jsonPath("$.code").value("CONTEXT_AMBIGUOUS")).andExpect(jsonPath("$.nextRequestId").value("new-nonce"));verifyNoInteractions(rag);
    }
    @Test void blankQuestionAndOrphanNonceAreRejected() throws Exception {
        mvc.perform(post("/api/rag/ask").contentType("application/json").content("{\"question\":\" \"}")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/rag/ask").contentType("application/json").content("{\"question\":\"x\",\"requestId\":\"orphan\"}")).andExpect(status().isBadRequest());verifyNoInteractions(rag);
    }
    @Test void protectedCompletionRejectionKeepsStatusCodeAndRetryAfter() throws Exception {
        when(sessions.ask(anyString(),anyString(),anyString())).thenThrow(new ConversationException(503,"AI_CIRCUIT_OPEN","temporary").retryAfter(30));
        mvc.perform(post("/api/rag/ask").contentType("application/json").content("{\"question\":\"x\",\"conversationToken\":\"xxx\",\"requestId\":\"yyy\"}"))
                .andExpect(status().isServiceUnavailable()).andExpect(header().string("Retry-After","30")).andExpect(jsonPath("$.code").value("AI_CIRCUIT_OPEN"));
    }
}
