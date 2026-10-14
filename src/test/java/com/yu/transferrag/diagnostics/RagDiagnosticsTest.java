package com.yu.transferrag.diagnostics;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.openai.core.http.Headers;
import com.openai.errors.*;
import com.yu.transferrag.protection.ProtectionException;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.mock.web.*;
import javax.net.ssl.SSLHandshakeException;
import java.io.*;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.stream.Stream;
import static org.assertj.core.api.Assertions.*;
import static com.yu.transferrag.diagnostics.RagDiagnostics.Stage.*;

class RagDiagnosticsTest {
    static final String SECRET="PRIVATE_QUESTION_KEY_PROMPT_VECTOR_PASSWORD";
    Logger logger;ListAppender<ILoggingEvent> logs;
    @BeforeEach void start(){logger=(Logger)LoggerFactory.getLogger(RagDiagnostics.class);logs=new ListAppender<>();logs.start();logger.addAppender(logs);}
    @AfterEach void stop(){logger.detachAppender(logs);logs.stop();assertThat(RagDiagnostics.requestId()).isNull();}
    static Stream<Object[]> failures(){
        SocketTimeoutException connect=new SocketTimeoutException(SECRET);
        connect.setStackTrace(new StackTraceElement[]{new StackTraceElement("sun.nio.ch.NioSocketImpl","timedFinishConnect","NioSocketImpl.java",1)});
        SocketTimeoutException read=new SocketTimeoutException(SECRET);
        read.setStackTrace(new StackTraceElement[]{new StackTraceElement("sun.nio.ch.NioSocketImpl","timedRead","NioSocketImpl.java",1)});
        return Stream.of(
                new Object[]{connect,"CONNECT_TIMEOUT"},
                new Object[]{new HttpConnectTimeoutException(SECRET),"CONNECT_TIMEOUT"},
                new Object[]{read,"READ_OR_REQUEST_TIMEOUT"},
                new Object[]{new SocketTimeoutException(SECRET),"TIMEOUT_UNSPECIFIED"},
                new Object[]{new HttpTimeoutException(SECRET),"READ_OR_REQUEST_TIMEOUT"},
                new Object[]{new UnknownHostException(SECRET),"DNS_FAILURE"},
                new Object[]{new SSLHandshakeException(SECRET),"TLS_FAILURE"},
                new Object[]{new EOFException(SECRET),"EOF"},
                new Object[]{new SocketException("Connection reset"),"CONNECTION_RESET"},
                new Object[]{new SocketException(SECRET),"SOCKET_IO"},
                new Object[]{new ConnectException(SECRET),"CONNECTION_FAILURE"},
                new Object[]{new InterruptedIOException(SECRET),"INTERRUPTED_IO"},
                new Object[]{InternalServerException.builder().statusCode(503).headers(Headers.builder().build()).build(),"HTTP_5XX"},
                new Object[]{RateLimitException.builder().headers(Headers.builder().build()).build(),"HTTP_4XX"},
                new Object[]{UnauthorizedException.builder().headers(Headers.builder().build()).build(),"HTTP_4XX"},
                new Object[]{new ProtectionException(503,"AI_CIRCUIT_OPEN",SECRET,30),"CIRCUIT_OPEN"},
                new Object[]{new ProtectionException(503,"AI_BUSY",SECRET,1),"BULKHEAD_REJECTED"});
    }
    @ParameterizedTest @MethodSource("failures")
    void classifiedFailuresNeverLogMessagesOrThrowable(Throwable root,String category){
        RuntimeException wrapped=root instanceof RuntimeException runtime?runtime:new OpenAIIoException(SECRET,root);
        try(var scope=RagDiagnostics.begin()){
            assertThatThrownBy(()->RagDiagnostics.measure(ANSWERABILITY,()->{throw wrapped;})).isSameAs(wrapped);
            scope.status(503);
        }
        assertThat(logs.list).hasSize(2).allSatisfy(event->{
            assertThat(event.getFormattedMessage()).contains("category="+category).doesNotContain(SECRET);
            assertThat(event.getThrowableProxy()).isNull();
        });
        assertThat(logs.list.getLast().getFormattedMessage()).contains("failed_stage=ANSWERABILITY","http_attempts=NOT_OBSERVABLE");
    }
    @Test void unknownExceptionClassIsNotPrinted(){
        class PrivateName extends RuntimeException {PrivateName(){super(SECRET);}}
        assertThat(RagDiagnostics.classify(new PrivateName()).outer()).isEqualTo("OTHER");
        assertThat(RagDiagnostics.classify(new PrivateName()).root()).isEqualTo("OTHER");
    }
    @Test void cyclicOrTruncatedCausesDoNotPretendToIdentifyTheRoot(){
        RuntimeException cyclic=new RuntimeException(SECRET){@Override public synchronized Throwable getCause(){return this;}};
        assertThat(RagDiagnostics.classify(cyclic).root()).isEqualTo("NOT_OBSERVABLE");
        RuntimeException longChain=new RuntimeException(SECRET);
        for(int i=0;i<40;i++)longChain=new RuntimeException(SECRET,longChain);
        assertThat(RagDiagnostics.classify(longChain).root()).isEqualTo("NOT_OBSERVABLE");
        var interrupted=new InterruptedIOException(SECRET);interrupted.initCause(new SocketException(SECRET));
        assertThat(RagDiagnostics.classify(new OpenAIIoException(SECRET,interrupted)).category()).isEqualTo(RagDiagnostics.Category.INTERRUPTED_IO);
    }
    @Test void noScopeIsNoOpAndDoesNotTraceImports(){
        assertThat(RagDiagnostics.measure(QUERY_EMBEDDING,()->"unchanged")).isEqualTo("unchanged");
        assertThat(logs.list).isEmpty();
    }
    @Test void nestedEmbeddingTimeIsNotDoubleCountedAsRetrieval() {
        try(var scope=RagDiagnostics.begin()){
            RagDiagnostics.measure(RETRIEVAL,()->RagDiagnostics.measure(QUERY_EMBEDDING,()->{
                try{Thread.sleep(20);}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}return 1;
            }));
        }
        var retrieval=logs.list.stream().map(ILoggingEvent::getFormattedMessage).filter(s->s.contains("stage=RETRIEVAL ")).findFirst().orElseThrow();
        long elapsed=number(retrieval,"elapsed_us"),exclusive=number(retrieval,"exclusive_us");
        assertThat(elapsed).isGreaterThanOrEqualTo(15_000);assertThat(exclusive).isLessThan(elapsed);
        assertThat(logs.list.getLast().getFormattedMessage()).contains("query_embedding_calls=1","retrieval_calls=1");
    }
    static long number(String text,String key){var m=java.util.regex.Pattern.compile("(?:^| )"+key+"=(\\d+)").matcher(text);assertThat(m.find()).isTrue();return Long.parseLong(m.group(1));}
    @Test void filterIgnoresForgedHeadersAndCleansThreadAfterSuccessAndException() throws Exception {
        var filter=new RagDiagnosticsFilter();var ids=new ArrayList<String>();
        MDC.put("unrelated","retained");
        try{
            for(int i=0;i<2;i++){
                var request=new MockHttpServletRequest("POST","/api/rag/ask");request.addHeader("X-Request-ID",SECRET);
                request.addHeader("X-RAG-Request-ID",SECRET);request.addHeader("X-Correlation-ID",SECRET);
                var response=new MockHttpServletResponse();final int attempt=i;
                org.assertj.core.api.ThrowableAssert.ThrowingCallable call=()->filter.doFilter(request,response,(req,res)->{
                    ids.add(RagDiagnostics.requestId());assertThat(MDC.get(RagDiagnostics.MDC_KEY)).isEqualTo(ids.getLast());
                    if(attempt==1)throw new ServletException(SECRET);
                });
                if(i==0)assertThatCode(call).doesNotThrowAnyException();else assertThatThrownBy(call).isInstanceOf(ServletException.class);
                assertThat(RagDiagnostics.requestId()).isNull();assertThat(MDC.get(RagDiagnostics.MDC_KEY)).isNull();
                assertThat(MDC.get("unrelated")).isEqualTo("retained");
            }
            assertThat(ids).doesNotHaveDuplicates().allSatisfy(id->assertThat(id).matches("[0-9a-f]{32}"));
            assertThat(logs.list.getLast().getFormattedMessage()).contains("status=500").doesNotContain(SECRET);
        }finally{MDC.remove("unrelated");}
    }
    @Test void filterDoesNotInstrumentOtherEndpoints() throws Exception {
        var filter=new RagDiagnosticsFilter();var request=new MockHttpServletRequest("GET","/api/public/sources/1");
        filter.doFilter(request,new MockHttpServletResponse(),(req,res)->assertThat(RagDiagnostics.requestId()).isNull());
        assertThat(logs.list).isEmpty();
    }
    @Test void mappedContextPathAndMatrixParametersDoNotBypassDiagnostics() throws Exception {
        var request=new MockHttpServletRequest("POST","/compass/api/rag/ask;fixture=1");request.setContextPath("/compass");
        new RagDiagnosticsFilter().doFilter(request,new MockHttpServletResponse(),(req,res)->assertThat(RagDiagnostics.requestId()).matches("[a-f0-9]{32}"));
        assertThat(logs.list).hasSize(1);
    }
    @Test void nestedScopesRestorePreviousCorrelationAndMdc(){
        try(var outer=RagDiagnostics.begin()){
            String outerId=RagDiagnostics.requestId();
            try(var inner=RagDiagnostics.begin()){assertThat(RagDiagnostics.requestId()).isNotEqualTo(outerId);}
            assertThat(RagDiagnostics.requestId()).isEqualTo(outerId);assertThat(MDC.get(RagDiagnostics.MDC_KEY)).isEqualTo(outerId);
        }
        assertThat(MDC.get(RagDiagnostics.MDC_KEY)).isNull();
    }
}
