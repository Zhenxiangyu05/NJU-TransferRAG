package com.yu.transferrag.config;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.yu.transferrag.cache.*;
import com.yu.transferrag.controller.*;
import com.yu.transferrag.diagnostics.*;
import com.yu.transferrag.dto.SourceResponse;
import com.yu.transferrag.entity.*;
import com.yu.transferrag.exception.AiServiceUnavailableException;
import com.yu.transferrag.repository.*;
import com.yu.transferrag.service.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import javax.net.ssl.*;
import java.net.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Spring Chat/Embedding SDK and loopback HTTP/TLS, plus normal MVC error mapping/filter cleanup. */
class RagDiagnosticsHttpTest {
    final ChatProviderIsolationTest fixture=new ChatProviderIsolationTest();
    Logger logger;ListAppender<ILoggingEvent> logs;
    @BeforeEach void start() throws Exception {fixture.start();logger=(Logger)LoggerFactory.getLogger(RagDiagnostics.class);logs=new ListAppender<>();logs.start();logger.addAppender(logs);}
    @AfterEach void stop(){fixture.close();logger.detachAppender(logs);logs.stop();assertThat(RagDiagnostics.requestId()).isNull();}
    List<String> lines(){return logs.list.stream().map(ILoggingEvent::getFormattedMessage).toList();}
    static SourceResponse source(){var r=new SourceResponse();r.setCitationId("S1");r.setDocumentId(100L);return r;}

    @ParameterizedTest @CsvSource({"njusehub,canonical","njusehub,evidence","deepseek-official,canonical","deepseek-official,evidence"})
    void realProviderRagHasAllStageTimesCitationSafeLogsAndSchoolQueryIsolation(String provider,String route){
        fixture.run(fixture.context("app.ai.chat.provider="+provider),c->{
            var model=c.getBean(ChatModel.class);var original=c.getBean(OpenAiEmbeddingModel.class);
            var cache=new QueryEmbeddingCache(original,mock(EmbeddingCacheStore.class),
                    new EmbeddingCacheProperties(false,Duration.ofHours(24),0,1024,"v1","fixture","exact",Duration.ZERO),
                    ()->"school-only",new SimpleMeterRegistry());
            cache.setRemoteGuard(c.getBean(com.yu.transferrag.protection.AiRemoteGuard.class));
            var retrieval=mock(RetrievalService.class);var prepared=mock(RetrievalService.PreparedQuery.class);
            when(retrieval.prepareCanonicalFirst(anyString())).thenAnswer(i->{assertThat(cache.embed("synthetic query")).hasSize(1024);return prepared;});
            var evidence=new Document();evidence.setId(100L);evidence.setTitle("Synthetic");evidence.setDocumentRole(DocumentRole.EVIDENCE);evidence.setSourceType("PERSONAL");
            var docs=mock(DocumentRepository.class);when(docs.findAllById(any())).thenReturn(List.of(evidence));
            var refs=mock(EvidenceRefRepository.class);
            if(route.equals("canonical")){
                when(retrieval.searchCanonical(prepared,3)).thenReturn(List.of(ChatProviderIsolationTest.candidate(900,99,DocumentRole.CANONICAL,"[F0] Synthetic supported fact.")));
                var chunk=new Chunk();chunk.setId(900L);var ref=new EvidenceRef();ref.setId(1L);ref.setCanonicalChunk(chunk);ref.setFactIndex(0);ref.setEvidenceDocument(evidence);ref.setEvidenceText("Synthetic supported fact.");
                when(refs.findByCanonicalChunk_IdInOrderByCanonicalChunk_IdAscFactIndexAscIdAsc(List.of(900L))).thenReturn(List.of(ref));
            }else{
                when(retrieval.searchCanonical(prepared,3)).thenReturn(List.of());
                when(retrieval.searchEvidence(prepared,3)).thenReturn(List.of(ChatProviderIsolationTest.candidate(200,100,DocumentRole.EVIDENCE,"Synthetic supported fact.")));
            }
            var rag=new RagService(retrieval,new AnswerabilityService(model),model,docs,refs,true,PublishedEvidenceFixture.gate());
            var mvc=MockMvcBuilders.standaloneSetup(new RagController(rag)).setControllerAdvice(new GlobalExceptionHandler()).addFilters(new RagDiagnosticsFilter()).build();
            try{
                mvc.perform(post("/api/rag/ask").contentType("application/json").content("{\"question\":\"PRIVATE_SYNTHETIC_QUESTION\"}"))
                        .andExpect(status().isOk()).andExpect(jsonPath("$.answer").value("Synthetic supported fact. [S1]"))
                        .andExpect(jsonPath("$.sources[0].citationId").value("S1"))
                        .andExpect(jsonPath("$.sources[0].documentId").value(100))
                        .andExpect(jsonPath("$.sources[0].sourceType").value("PERSONAL"));
            }catch(Exception e){throw new AssertionError(e);}
            assertThat(lines()).anySatisfy(s->assertThat(s).contains("stage=QUERY_EMBEDDING","outcome=SUCCESS"))
                    .anySatisfy(s->assertThat(s).contains("stage=RETRIEVAL","outcome=SUCCESS"))
                    .anySatisfy(s->assertThat(s).contains("stage=ANSWERABILITY","outcome=SUCCESS"))
                    .anySatisfy(s->assertThat(s).contains("stage=GENERATION","outcome=SUCCESS"))
                    .anySatisfy(s->assertThat(s).contains("RAG diagnostic request:","status=200","answerability_calls=1","generation_calls=1","query_embedding_calls=1"));
            assertThat(logs.list).allSatisfy(e->{assertThat(e.getThrowableProxy()).isNull();assertThat(e.getFormattedMessage()).doesNotContain("PRIVATE_SYNTHETIC_QUESTION","Synthetic supported fact",fixture.school.url(),fixture.official.url(),ChatProviderIsolationTest.OFFICIAL_KEY,ChatProviderIsolationTest.SCHOOL_KEY);});
            var ids=lines().stream().map(s->{var m=java.util.regex.Pattern.compile("id=([a-f0-9]{32})").matcher(s);assertThat(m.find()).isTrue();return m.group(1);}).distinct().toList();
            assertThat(ids).hasSize(1);assertThat(RagDiagnostics.requestId()).isNull();
        });
    }

    @ParameterizedTest @ValueSource(strings={"503","timeout","eof"})
    void realHttpFailuresHaveSafeCauseClassificationAndUnchangedAttempts(String failure){
        if(failure.equals("503"))fixture.school.status=503;
        if(failure.equals("timeout"))fixture.school.delay=1000;
        if(failure.equals("eof"))fixture.school.disconnect=true;
        fixture.run(fixture.context("app.ai.chat.provider=njusehub","spring.ai.openai.chat.timeout="+(failure.equals("timeout")?"200ms":"2s")),c->{
            try(var scope=RagDiagnostics.begin()){
                assertThatThrownBy(()->new AnswerabilityService(c.getBean(ChatModel.class)).check("synthetic","[S1] synthetic",List.of(source())))
                        .isInstanceOfSatisfying(AiServiceUnavailableException.class,e->{
                            if(failure.equals("timeout")) {
                                boolean timed=false;for(Throwable node=e;node!=null;node=node.getCause())if(node instanceof java.io.InterruptedIOException)timed=true;
                                assertThat(timed).isTrue();
                            }
                        });scope.status(503);
            }
            assertThat(fixture.school.requests).hasSize(1);assertThat(fixture.official.requests).isEmpty();
            if(failure.equals("timeout")) {
                // The real call deadline and socket read timer can race. Do not turn an unknown
                // socket phase into a fictitious READ diagnosis merely to make this assertion pass.
                var event=lines().stream().filter(s->s.contains("RAG diagnostic stage:")).findFirst().orElseThrow();
                var matcher=java.util.regex.Pattern.compile("elapsed_us=(\\d+)").matcher(event);assertThat(matcher.find()).isTrue();
                assertThat(Long.parseLong(matcher.group(1))).isBetween(130_000L,850_000L);
                assertThat(event).containsAnyOf("category=READ_OR_REQUEST_TIMEOUT","category=TIMEOUT_UNSPECIFIED","category=INTERRUPTED_IO");
            }else{
                String category=failure.equals("503")?"HTTP_5XX":"EOF";
                assertThat(lines()).anySatisfy(s->assertThat(s).contains("failed_stage=ANSWERABILITY","category="+category,"http_attempts=NOT_OBSERVABLE"));
            }
        });
    }

    @Test void realTlsFailureIsNotMisclassifiedAsTimeout() throws Exception {
        SSLContext ssl=SSLContext.getInstance("TLS");ssl.init(null,null,null);
        try(var listener=(SSLServerSocket)ssl.getServerSocketFactory().createServerSocket(0,8,InetAddress.getByName("127.0.0.1"))){
            var pool=Executors.newSingleThreadExecutor();var connections=new AtomicInteger();
            try{
                pool.submit(()->{while(!listener.isClosed())try(var socket=(SSLSocket)listener.accept()){connections.incrementAndGet();socket.setSoTimeout(1500);socket.startHandshake();}catch(Exception ignored){}});
                fixture.run(fixture.context("app.ai.chat.provider=njusehub","spring.ai.openai.chat.base-url=https://127.0.0.1:"+listener.getLocalPort()+"/v1","spring.ai.openai.chat.timeout=2s"),c->{
                    try(var scope=RagDiagnostics.begin()){
                        assertThatThrownBy(()->new AnswerabilityService(c.getBean(ChatModel.class)).check("synthetic","[S1] synthetic",List.of(source()))).isInstanceOf(AiServiceUnavailableException.class);
                        scope.status(503);
                    }
                    assertThat(lines()).anySatisfy(s->assertThat(s).contains("category=TLS_FAILURE","root=javax.net.ssl.SSLHandshakeException"));
                    assertThat(connections).hasValue(1);assertThat(fixture.school.requests).isEmpty();assertThat(fixture.official.requests).isEmpty();
                });
            }finally{listener.close();pool.shutdownNow();}
        }
    }
    @Test void actualCircuitOpenIsLoggedWithoutSendingAnotherHttpRequest(){
        fixture.school.status=503;
        fixture.run(fixture.context("app.ai.chat.provider=njusehub","app.ai.resilience.minimum-calls=2","app.ai.resilience.window-size=4"),c->{
            var checker=new AnswerabilityService(c.getBean(ChatModel.class));
            for(int i=0;i<2;i++)assertThatThrownBy(()->checker.check("synthetic","[S1] synthetic",List.of(source()))).isInstanceOf(AiServiceUnavailableException.class);
            try(var scope=RagDiagnostics.begin()){
                assertThatThrownBy(()->checker.check("synthetic","[S1] synthetic",List.of(source())))
                        .isInstanceOf(com.yu.transferrag.protection.ProtectionException.class);scope.status(503);
            }
            assertThat(fixture.school.requests).hasSize(2);
            assertThat(lines()).anySatisfy(s->assertThat(s).contains("category=CIRCUIT_OPEN","failed_stage=ANSWERABILITY"));
        });
    }
    @Test void actualBulkheadRejectsFastAndReleasesPermitWithoutRetries(){
        fixture.school.delay=300;
        fixture.run(fixture.context("app.ai.chat.provider=njusehub","app.ai.resilience.chat-concurrency=1"),c->{
            var checker=new AnswerabilityService(c.getBean(ChatModel.class));var pool=Executors.newSingleThreadExecutor();
            try{
                var first=pool.submit(()->checker.check("synthetic","[S1] synthetic",List.of(source())));
                long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(2);
                while(fixture.school.requests.isEmpty()&&System.nanoTime()<deadline)Thread.sleep(5);
                assertThat(fixture.school.requests).hasSize(1);
                try(var scope=RagDiagnostics.begin()){
                    assertThatThrownBy(()->checker.check("synthetic","[S1] synthetic",List.of(source())))
                            .isInstanceOf(com.yu.transferrag.protection.ProtectionException.class);scope.status(503);
                }
                assertThat(first.get(3,TimeUnit.SECONDS).answerable()).isTrue();
                fixture.school.delay=0;assertThat(checker.check("synthetic","[S1] synthetic",List.of(source())).answerable()).isTrue();
                assertThat(fixture.school.requests).hasSize(2);
                assertThat(lines()).anySatisfy(s->assertThat(s).contains("category=BULKHEAD_REJECTED","failed_stage=ANSWERABILITY"));
            }catch(Exception e){throw new AssertionError(e);}finally{pool.shutdownNow();}
        });
    }
}
