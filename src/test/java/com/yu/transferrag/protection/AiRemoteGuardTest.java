package com.yu.transferrag.protection;

import com.sun.net.httpserver.HttpServer;
import com.openai.errors.*;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.ai.openai.*;
import org.springframework.ai.chat.prompt.Prompt;
import java.net.InetSocketAddress;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import com.yu.transferrag.cache.*;

/** Actual Spring AI OpenAI/SDK HTTP transport against an isolated loopback server. */
class AiRemoteGuardTest {
    final java.util.List<AiRemoteGuard> guards=new java.util.ArrayList<>();
    HttpServer server;ExecutorService executor;AtomicInteger requests=new AtomicInteger();volatile int status=200;volatile long delay;
    volatile String lastAuthorization;
    @BeforeEach void start() throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);executor=Executors.newFixedThreadPool(4);server.setExecutor(executor);
        server.createContext("/",exchange->{requests.incrementAndGet();lastAuthorization=exchange.getRequestHeaders().getFirst("Authorization");exchange.getRequestBody().readAllBytes();try{Thread.sleep(delay);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            String body=status!=200?"{\"error\":{\"message\":\"synthetic\",\"type\":\"test\",\"code\":\"test\"}}":exchange.getRequestURI().getPath().contains("embeddings")?
                    "{\"object\":\"list\",\"model\":\"test\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":[1,0,0]}],\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}":
                    "{\"id\":\"test\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"test\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":\"OK\"},\"finish_reason\":\"stop\"}]}";
            try{exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,body.getBytes().length);exchange.getResponseBody().write(body.getBytes());}catch(java.io.IOException ignored){}finally{exchange.close();}
        });server.start();
    }
    @AfterEach void stop(){try{guards.forEach(AiRemoteGuard::close);}finally{server.stop(0);executor.shutdownNow();}}
    String base(){return "http://127.0.0.1:"+server.getAddress().getPort();}
    AiResilienceProperties config(boolean enabled,int retry,Duration timeout){return new AiResilienceProperties(enabled,4,2,50,Duration.ofMillis(100),2,1,1,timeout,timeout,retry,Duration.ofMillis(1));}
    AiRemoteGuard guard(boolean enabled,int retry,Duration timeout){var g=new AiRemoteGuard(config(enabled,retry,timeout),new SimpleMeterRegistry());guards.add(g);return g;}
    OpenAiEmbeddingModel embedding(){return OpenAiEmbeddingModel.builder().options(OpenAiEmbeddingOptions.builder().baseUrl(base()).apiKey("synthetic-test-token").model("test").dimensions(3).maxRetries(0).timeout(Duration.ofSeconds(2)).build()).build();}
    OpenAiChatModel chat(){return OpenAiChatModel.builder().options(OpenAiChatOptions.builder().baseUrl(base()).apiKey("synthetic-test-token").model("test").maxRetries(0).timeout(Duration.ofSeconds(2)).build()).build();}
    @Test void normalHttpEmbedding(){var g=guard(true,0,Duration.ofSeconds(2));assertArrayEquals(new float[]{1,0,0},g.embed(embedding(),"q"));assertEquals(1,requests.get());}
    @Test void closingOwnedQueryClientIsIdempotentAndDoesNotCloseOriginal(){
        var g=guard(true,0,Duration.ofSeconds(2));var original=embedding();
        assertArrayEquals(new float[]{1,0,0},g.embed(original,"q"));g.close();g.close();
        assertThrows(IllegalStateException.class,()->g.embed(original,"q"));assertEquals(1,requests.get());
        assertArrayEquals(new float[]{1,0,0},original.embed("q"));assertEquals(2,requests.get());
    }
    @Test void failuresOpenFastRejectHalfOpenThenClose() throws Exception {
        var g=guard(true,0,Duration.ofSeconds(2));var m=embedding();status=503;
        assertThrows(OpenAIServiceException.class,()->g.embed(m,"q"));assertThrows(OpenAIServiceException.class,()->g.embed(m,"q"));assertEquals(CircuitBreaker.State.OPEN,g.breaker("queryEmbedding").getState());
        assertEquals("AI_CIRCUIT_OPEN",assertThrows(ProtectionException.class,()->g.embed(m,"q")).code());assertEquals(2,requests.get());
        Thread.sleep(130);status=200;g.embed(m,"q");assertEquals(CircuitBreaker.State.HALF_OPEN,g.breaker("queryEmbedding").getState());g.embed(m,"q");assertEquals(CircuitBreaker.State.CLOSED,g.breaker("queryEmbedding").getState());
    }
    @Test void independentChatCircuit(){var g=guard(true,0,Duration.ofSeconds(2));var m=embedding();status=503;assertThrows(OpenAIServiceException.class,()->g.embed(m,"q"));assertThrows(OpenAIServiceException.class,()->g.embed(m,"q"));status=200;assertNotNull(g.call("deepseekChat",()->chat().call(new Prompt("test"))));assertEquals(CircuitBreaker.State.CLOSED,g.breaker("deepseekChat").getState());}
    @Test void sdkRetriesDisabledAndOneExtraRetryOnly(){var g=guard(true,1,Duration.ofSeconds(2));status=503;assertThrows(OpenAIServiceException.class,()->g.embed(embedding(),"q"));assertEquals(2,requests.get());}
    @Test void authenticationNeverRetriedOrOpensCircuit(){var g=guard(true,1,Duration.ofSeconds(2));status=401;var m=embedding();for(int i=0;i<3;i++)assertThrows(OpenAIServiceException.class,()->g.embed(m,"q"));assertEquals(3,requests.get());assertEquals(CircuitBreaker.State.CLOSED,g.breaker("queryEmbedding").getState());}
    @Test void actualNetworkTimeoutIsBounded(){var g=guard(true,0,Duration.ofMillis(150));delay=2000;long start=System.nanoTime();assertThrows(OpenAIIoException.class,()->g.embed(embedding(),"q"));assertTrue(Duration.ofNanos(System.nanoTime()-start).toMillis()<1500);assertEquals(1,requests.get());}
    @Test void semaphoreFastRejectAndRelease() throws Exception {
        var g=guard(true,0,Duration.ofSeconds(2));var started=new CountDownLatch(1);var release=new CountDownLatch(1);var thread=new Thread(()->g.call("deepseekChat",()->{started.countDown();try{release.await();}catch(InterruptedException e){Thread.currentThread().interrupt();}return true;}));thread.start();assertTrue(started.await(2,TimeUnit.SECONDS));
        try{assertEquals("AI_BUSY",assertThrows(ProtectionException.class,()->g.call("deepseekChat",()->true)).code());assertEquals(0,g.breaker("deepseekChat").getMetrics().getNumberOfFailedCalls());}finally{release.countDown();thread.join(2000);}assertTrue(g.call("deepseekChat",()->true));
    }
    @Test void disabledBypassesProtectionAndKeepsOriginalModel(){var g=guard(false,0,Duration.ofMillis(1));assertArrayEquals(new float[]{1,0,0},g.embed(embedding(),"q"));assertEquals(0,g.breaker("queryEmbedding").getMetrics().getNumberOfSuccessfulCalls());}
    @Test void inputErrorsNotCircuitFailures(){var g=guard(true,0,Duration.ofSeconds(2));for(int i=0;i<5;i++)assertThrows(IllegalArgumentException.class,()->g.call("deepseekChat",()->{throw new IllegalArgumentException();}));assertEquals(CircuitBreaker.State.CLOSED,g.breaker("deepseekChat").getState());}
    @Configuration(proxyBeanMethods=false)
    static class Beans {
        @Bean OpenAiChatProperties chatProperties(Environment e){var p=new OpenAiChatProperties();p.setBaseUrl(e.getRequiredProperty("test.base-url"));p.setApiKey("synthetic-test-token");p.setModel("test");return p;}
        @Bean ChatModel chatModel(OpenAiChatProperties p){return OpenAiChatModel.builder().options(p.toOptions().mutate().baseUrl(p.getBaseUrl()).apiKey(p.getApiKey()).maxRetries(p.getOptions().getMaxRetries()!=null?p.getOptions().getMaxRetries():p.getMaxRetries()).timeout(p.getOptions().getTimeout()!=null?p.getOptions().getTimeout():p.getTimeout()).build()).build();}
    }
    @Test void realSpringChatBeanIsProtectedAndSdkDoesNotRetry(){
        new ApplicationContextRunner().withUserConfiguration(AiResilienceConfiguration.class,Beans.class)
                .withPropertyValues("test.base-url="+base(),"app.ai.resilience.enabled=true","app.ai.resilience.minimum-calls=2","app.ai.resilience.window-size=4")
                .run(c->{assertNull(c.getStartupFailure());status=503;var m=c.getBean(ChatModel.class);
                    assertThrows(OpenAIServiceException.class,()->m.call(new Prompt("test")));assertThrows(OpenAIServiceException.class,()->m.call(new Prompt("test")));
                    assertEquals("AI_CIRCUIT_OPEN",assertThrows(ProtectionException.class,()->m.call(new Prompt("test"))).code());assertEquals(2,requests.get());});
    }
    @Test void realSpringChatBeanDisabledKeepsOriginalType(){
        new ApplicationContextRunner().withUserConfiguration(AiResilienceConfiguration.class,Beans.class).withPropertyValues("test.base-url="+base())
                .run(c->{assertNull(c.getStartupFailure());assertInstanceOf(OpenAiChatModel.class,c.getBean(ChatModel.class));assertNotNull(c.getBean(ChatModel.class).call(new Prompt("test")));});
    }
    @Test void cacheHitWorksWithOpenEmbeddingCircuitAndMissIsProtected(){
        var g=guard(true,0,Duration.ofSeconds(2));var data=new ConcurrentHashMap<String,byte[]>();
        EmbeddingCacheStore store=new EmbeddingCacheStore(){public byte[] get(String k){return data.get(k);}public void put(String k,byte[] b,Duration ttl){data.put(k,b);}};
        var cache=new QueryEmbeddingCache(embedding(),store,new EmbeddingCacheProperties(true,Duration.ofHours(1),0,3,"v1","test","raw-v1",Duration.ofSeconds(5)),()->"test",new SimpleMeterRegistry());cache.setRemoteGuard(g);
        assertArrayEquals(new float[]{1,0,0},cache.embed("same"));assertEquals(1,requests.get());g.breaker("queryEmbedding").transitionToOpenState();
        assertArrayEquals(new float[]{1,0,0},cache.embed("same"));assertEquals(1,requests.get());
        assertEquals("AI_CIRCUIT_OPEN",assertThrows(ProtectionException.class,()->cache.embed("different")).code());assertEquals(1,requests.get());assertEquals(1,cache.stats().embeddingCalls());
    }
    @Test void actualSpringAiAutoConfigurationBeanUsesGuard(){
        new ApplicationContextRunner().withUserConfiguration(AiResilienceConfiguration.class)
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(org.springframework.ai.model.openai.autoconfigure.OpenAiChatAutoConfiguration.class))
                .withBean(org.springframework.ai.model.tool.ToolCallingManager.class,()->org.mockito.Mockito.mock(org.springframework.ai.model.tool.ToolCallingManager.class))
                .withPropertyValues("spring.ai.openai.base-url="+base(),"spring.ai.openai.api-key=synthetic-test-token","spring.ai.openai.chat.options.model=test",
                        "spring.ai.openai.chat.options.max-retries=5","app.ai.resilience.enabled=true","app.ai.resilience.minimum-calls=2","app.ai.resilience.window-size=4")
                .run(c->{assertNull(c.getStartupFailure());status=503;var model=c.getBean(ChatModel.class);
                    assertThrows(OpenAIServiceException.class,()->model.call(new Prompt("test")));assertThrows(OpenAIServiceException.class,()->model.call(new Prompt("test")));
                    assertEquals("AI_CIRCUIT_OPEN",assertThrows(ProtectionException.class,()->model.call(new Prompt("test"))).code());assertEquals(2,requests.get());});
    }
    @Test void actualEmbeddingAutoConfigurationPreservesResolvedConnectionAndImportClient(){
        new ApplicationContextRunner().withUserConfiguration(AiResilienceConfiguration.class)
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration.class))
                .withPropertyValues("spring.ai.openai.base-url="+base(),"spring.ai.openai.api-key=synthetic-test-token",
                        "spring.ai.openai.embedding.model=test","spring.ai.openai.embedding.dimensions=3",
                        "app.ai.resilience.enabled=true","app.ai.resilience.minimum-calls=2","app.ai.resilience.window-size=4")
                .run(c->{assertNull(c.getStartupFailure());var original=c.getBean(OpenAiEmbeddingModel.class);var g=c.getBean(AiRemoteGuard.class);
                    assertNull(original.getOptions().getBaseUrl());assertNull(original.getOptions().getApiKey());
                    assertArrayEquals(new float[]{1,0,0},g.embed(original,"test"));assertEquals(1,requests.get());
                    assertEquals("Bearer synthetic-test-token",lastAuthorization);g.breaker("queryEmbedding").reset();
                    status=503;assertThrows(OpenAIServiceException.class,()->g.embed(original,"test"));assertEquals(2,requests.get());
                    assertThrows(OpenAIServiceException.class,()->g.embed(original,"test"));assertEquals(3,requests.get());
                    assertEquals("AI_CIRCUIT_OPEN",assertThrows(ProtectionException.class,()->g.embed(original,"test")).code());assertEquals(3,requests.get());
                    status=200;assertArrayEquals(new float[]{1,0,0},original.embed("import remains original"));assertEquals(4,requests.get());});
    }
    @Test void modelSpecificEmbeddingConnectionOverridesCommonProperties(){
        new ApplicationContextRunner().withUserConfiguration(AiResilienceConfiguration.class)
                .withConfiguration(org.springframework.boot.autoconfigure.AutoConfigurations.of(org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingAutoConfiguration.class))
                .withPropertyValues("spring.ai.openai.base-url=http://127.0.0.1:1","spring.ai.openai.api-key=synthetic-common-token",
                        "spring.ai.openai.embedding.base-url="+base(),"spring.ai.openai.embedding.api-key=synthetic-model-token",
                        "spring.ai.openai.embedding.model=test","spring.ai.openai.embedding.dimensions=3","app.ai.resilience.enabled=true")
                .run(c->{assertNull(c.getStartupFailure());assertArrayEquals(new float[]{1,0,0},c.getBean(AiRemoteGuard.class).embed(c.getBean(OpenAiEmbeddingModel.class),"test"));
                    assertEquals(1,requests.get());assertEquals("Bearer synthetic-model-token",lastAuthorization);});
    }
}
