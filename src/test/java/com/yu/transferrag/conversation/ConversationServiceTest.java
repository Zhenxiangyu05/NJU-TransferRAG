package com.yu.transferrag.conversation;

import com.yu.transferrag.dto.*;
import com.yu.transferrag.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class ConversationServiceTest {
    /** Test-only store; does not substitute for Redis integration evidence. */
    static class MemoryStore implements ConversationStore {
        final Map<String,State> rows = new HashMap<>(); final Map<String,String> locks = new HashMap<>();
        public synchronized Session create() { String t = UUID.randomUUID().toString(); State s = State.empty(); rows.put(t,s); return new Session(t,s.nextRequestId()); }
        public synchronized Claim acquire(String token) {
            if(!rows.containsKey(token))throw ConversationException.expired();
            if(locks.containsKey(token))throw ConversationException.busy();
            String owner=UUID.randomUUID().toString(); locks.put(token,owner);return new Claim(token,owner,rows.get(token));
        }
        public synchronized void save(Claim c,State s) {if(!Objects.equals(locks.get(c.key()),c.owner())||!rows.containsKey(c.key()))throw ConversationException.busy();rows.put(c.key(),s);}
        public synchronized void release(Claim c) {locks.remove(c.key(),c.owner());}
        public synchronized void delete(Claim c) {rows.remove(c.key());}
    }
    MemoryStore store; StandaloneQuestionRewriter rewriter; RagService rag; EvidenceReadinessService ready; ConversationService service;
    @BeforeEach void setup() {
        store=new MemoryStore();rewriter=mock(StandaloneQuestionRewriter.class);rag=mock(RagService.class);ready=mock(EvidenceReadinessService.class);
        ObjectProvider<ConversationStore> stores=mock(ObjectProvider.class);when(stores.getIfAvailable()).thenReturn(store);
        service=new ConversationService(stores,StandaloneQuestionRewriterTest.properties(),rewriter,rag,ready);
        when(rewriter.resolve(anyString(),anyList())).thenAnswer(i->new StandaloneQuestionRewriter.Resolution(i.getArgument(0),false,false));
        when(rag.ask(anyString())).thenAnswer(i->answer(i.getArgument(0)));
        when(rag.askWithContext(anyString(),anyString())).thenAnswer(i->answer(i.getArgument(0)));
        when(ready.readyDocumentIds(any())).thenReturn(Set.of(16L));
    }
    static RagResponse answer(String q) {var r=new RagResponse();r.setQuestion(q);r.setAnswer("Synthetic supported fact [S1]");var s=new SourceResponse();s.setDocumentId(16L);s.setCitationId("S1");r.setSources(List.of(s));return r;}
    String next(String token) {return store.rows.get(token).nextRequestId();}
    @Test void firstTurnAndDuplicateReturnSameAnswerWithoutAnotherModelCall() {
        var s=store.create();var a=service.ask(s.token(),s.requestId(),"完整问题");var b=service.ask(s.token(),s.requestId(),"完整问题");
        assertThat(a.getAnswer()).isEqualTo(b.getAnswer());assertThat(store.rows.get(s.token()).turns()).hasSize(1);verify(rag,times(1)).ask(anyString());verify(ready).readyDocumentIds(List.of(16L));
    }
    @Test void replayRechecksPublicationRatherThanCachingReadiness() {
        var s=store.create();service.ask(s.token(),s.requestId(),"完整问题");when(ready.readyDocumentIds(any())).thenReturn(Set.of());
        assertThatThrownBy(()->service.ask(s.token(),s.requestId(),"完整问题")).isInstanceOf(ConversationException.class);verify(rag,times(1)).ask(anyString());
    }
    @Test void followupUsesServerHistoryAndIndependentQuestion() {
        var s=store.create();service.ask(s.token(),s.requestId(),"软件工程需要什么条件？");
        when(rewriter.resolve(eq("那考试呢"),anyList())).thenReturn(new StandaloneQuestionRewriter.Resolution("软件工程考试考什么？",true,true));
        service.ask(s.token(),next(s.token()),"那考试呢");verify(rag).askWithContext("那考试呢","软件工程考试考什么？");
        assertThat(store.rows.get(s.token()).turns()).hasSize(2);
    }
    @Test void topicSwitchClearsPreviousChain() {
        var s=store.create();service.ask(s.token(),s.requestId(),"软件工程问题");service.ask(s.token(),next(s.token()),"图书馆什么时候开放？");
        assertThat(store.rows.get(s.token()).turns()).hasSize(1).first().extracting(ConversationStore.Turn::question).isEqualTo("图书馆什么时候开放？");
    }
    @Test void onlyFourRecentBoundedSuccessfulTurnsAreKept() {
        when(rewriter.resolve(anyString(),anyList())).thenAnswer(i->new StandaloneQuestionRewriter.Resolution(i.getArgument(0),true,true));
        var s=store.create();for(int i=0;i<7;i++)service.ask(s.token(),next(s.token()),"问题"+i);
        assertThat(store.rows.get(s.token()).turns()).hasSize(4).first().extracting(ConversationStore.Turn::question).isEqualTo("问题3");
    }
    @Test void resetInvalidatesOldCapabilityAndNewSessionIsIsolated() {
        var s=store.create();service.ask(s.token(),s.requestId(),"软件工程问题");var created=service.newConversation(s.token());
        assertThatThrownBy(()->service.ask(s.token(),nextRequest(),"那考试呢")).isInstanceOf(ConversationException.class);
        assertThat(store.rows.get(created.get("conversationToken")).turns()).isEmpty();
    }
    static String nextRequest() {return UUID.randomUUID().toString();}
    @Test void failedRagDoesNotAppendHistoryOrAutomaticallyRepeat() {
        var s=store.create();when(rag.ask(anyString())).thenThrow(new IllegalStateException("private fixture"));
        assertThatThrownBy(()->service.ask(s.token(),s.requestId(),"完整问题")).isInstanceOf(ConversationException.class).hasMessageNotContaining("private fixture");
        assertThat(store.rows.get(s.token()).turns()).isEmpty();
        assertThatThrownBy(()->service.ask(s.token(),s.requestId(),"完整问题")).isInstanceOf(ConversationException.class);verify(rag,times(1)).ask(anyString());
    }
    @Test void completionFailureNeverCallsRagOrAppendsHistory() {
        var s=store.create();when(rewriter.resolve(anyString(),anyList())).thenThrow(ConversationException.clarify("CONTEXT_COMPLETION_FAILED"));
        assertThatThrownBy(()->service.ask(s.token(),s.requestId(),"那考试呢")).isInstanceOf(ConversationException.class);verifyNoInteractions(rag);assertThat(store.rows.get(s.token()).turns()).isEmpty();
    }
    @Test void duplicateIdWithDifferentQuestionIsRejected() {
        var s=store.create();service.ask(s.token(),s.requestId(),"软件工程问题");
        assertThatThrownBy(()->service.ask(s.token(),s.requestId(),"计算机问题")).isInstanceOf(ConversationException.class);verify(rag,times(1)).ask(anyString());
    }
    @Test void oldNonceCannotBecomeValidAfterReceiptTruncation() {
        var s=store.create();for(int i=0;i<10;i++)service.ask(s.token(),next(s.token()),"问题"+i);
        assertThatThrownBy(()->service.ask(s.token(),s.requestId(),"问题0")).isInstanceOf(ConversationException.class);verify(rag,times(10)).ask(anyString());
    }
    @Test void concurrentRequestsAreRejectedWithoutQueueing() throws Exception {
        var entered=new CountDownLatch(1);var finish=new CountDownLatch(1);var s=store.create();
        when(rag.ask(anyString())).thenAnswer(i->{entered.countDown();assertThat(finish.await(3,TimeUnit.SECONDS)).isTrue();return answer(i.getArgument(0));});
        try(var executor=Executors.newSingleThreadExecutor()) {
            var first=executor.submit(()->service.ask(s.token(),s.requestId(),"完整问题"));assertThat(entered.await(3,TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(()->service.ask(s.token(),s.requestId(),"完整问题")).isInstanceOf(ConversationException.class);
            finish.countDown();assertThat(first.get(3,TimeUnit.SECONDS).getAnswer()).isNotBlank();verify(rag,times(1)).ask(anyString());
        } finally {finish.countDown();}
    }
    @Test void absentOrExpiredSessionFailsClosed() {
        assertThatThrownBy(()->service.ask("unknown",nextRequest(),"那考试呢")).isInstanceOf(ConversationException.class);verifyNoInteractions(rag,rewriter);
    }
    @Test void disabledFeatureNeverAcquiresOrCreatesState() {
        ObjectProvider<ConversationStore> stores=mock(ObjectProvider.class);
        var defaults=StandaloneQuestionRewriterTest.properties();
        var disabled=new ConversationProperties(false,defaults.ttl(),defaults.processingLease(),defaults.completionTimeout(),4,500,4000);
        var off=new ConversationService(stores,disabled,rewriter,rag,ready);
        assertThatThrownBy(()->off.newConversation(null)).isInstanceOf(ConversationException.class);
        assertThatThrownBy(()->off.ask("token",nextRequest(),"完整问题")).isInstanceOf(ConversationException.class);verifyNoInteractions(stores,rag);
    }
    @Test void contextTextLimitTrimsWholeTurnsInsteadOfCuttingConditions() {
        ObjectProvider<ConversationStore> stores=mock(ObjectProvider.class);when(stores.getIfAvailable()).thenReturn(store);
        var d=StandaloneQuestionRewriterTest.properties();
        var limited=new ConversationService(stores,new ConversationProperties(true,d.ttl(),d.processingLease(),d.completionTimeout(),4,500,1000),rewriter,rag,ready);
        when(rewriter.resolve(anyString(),anyList())).thenAnswer(i->new StandaloneQuestionRewriter.Resolution(i.getArgument(0),true,true));
        var s=store.create();for(int i=0;i<3;i++)limited.ask(s.token(),next(s.token()),"x".repeat(400)+i);
        assertThat(store.rows.get(s.token()).turns()).hasSize(1);assertThat(store.rows.get(s.token()).turns().getFirst().question()).hasSize(401);
    }
    @Test void diagnosticsNeverEmitQuestionHistoryOrCapability() {
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(ConversationService.class);
        var logs=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();logs.start();logger.addAppender(logs);
        var s=store.create();
        try(var scope=com.yu.transferrag.diagnostics.RagDiagnostics.begin()) {
            service.ask(s.token(),s.requestId(),"PRIVATE_SYNTHETIC_QUESTION");
            assertThat(logs.list).isNotEmpty().allSatisfy(e->{assertThat(e.getThrowableProxy()).isNull();assertThat(e.getFormattedMessage()).doesNotContain("PRIVATE_SYNTHETIC_QUESTION",s.token(),s.requestId());});
        } finally {logger.detachAppender(logs);logs.stop();}
    }
    @Test void requestNonceFromAnotherSessionCannotInvokeModels() {
        var one=store.create();var two=store.create();
        assertThatThrownBy(()->service.ask(two.token(),one.requestId(),"完整问题")).isInstanceOf(ConversationException.class);verifyNoInteractions(rag,rewriter);
    }
}
