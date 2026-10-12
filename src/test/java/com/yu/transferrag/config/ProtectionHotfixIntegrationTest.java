package com.yu.transferrag.config;

import com.yu.transferrag.cache.QueryEmbeddingCache;
import com.yu.transferrag.controller.*;
import com.yu.transferrag.protection.*;
import com.yu.transferrag.service.*;
import io.qdrant.client.QdrantClient;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.*;
import org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.Duration;
import java.util.UUID;
import java.util.function.Consumer;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Redis + real auto-configured SDK/HTTP stub; Qdrant/RAG integration is also rerun separately. */
@Testcontainers(disabledWithoutDocker=true)
class ProtectionHotfixIntegrationTest {
    static final String PASSWORD=UUID.randomUUID().toString();
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379).withCommand("redis-server","--requirepass",PASSWORD,"--save","","--appendonly","no")
            .withCreateContainerCmdModifier(cmd->cmd.getHostConfig().withPortBindings(new com.github.dockerjava.api.model.PortBinding(
                    com.github.dockerjava.api.model.Ports.Binding.bindIpAndPort("127.0.0.1",0),new com.github.dockerjava.api.model.ExposedPort(6379))));
    final ChatTimeoutConfigurationTest fixture=new ChatTimeoutConfigurationTest();
    @BeforeEach void start() throws Exception {fixture.start();}
    @AfterEach void stop(){fixture.stop();}
    @Configuration(proxyBeanMethods=false)
    static class VectorFixture { @Bean QdrantClient client(){return mock(QdrantClient.class);} }

    ApplicationContextRunner context(boolean lua,boolean resilience,String timeout) {
        return fixture.runner("spring.ai.openai.chat.timeout="+timeout)
                .withConfiguration(AutoConfigurations.of(QdrantVectorStoreAutoConfiguration.class))
                .withUserConfiguration(AiResilienceConfiguration.class,QueryEmbeddingCacheConfiguration.class,
                        RateLimitConfiguration.class,VectorFixture.class,ChatTimeoutConfigurationTest.RagFixture.class)
                .withPropertyValues("app.cache.embedding.enabled=true","app.cache.embedding.dimensions=3",
                        "app.cache.embedding.redis.host="+REDIS.getHost(),"app.cache.embedding.redis.port="+REDIS.getMappedPort(6379),
                        "app.cache.embedding.redis.password="+PASSWORD,"app.cache.embedding.version="+UUID.randomUUID().toString().replace("-", ""),
                        "app.rag.rate-limit.enabled="+lua,"app.ai.resilience.enabled="+resilience,
                        "app.rag.rate-limit.hmac-secret="+(lua?"synthetic-local-only-32-character-secret":""),
                        "app.rag.rate-limit.ip-per-minute=0.001","app.rag.rate-limit.ip-capacity=1",
                        "app.ai.resilience.minimum-calls=2","app.ai.resilience.window-size=4",
                        "app.ai.resilience.open-wait=30s","spring.ai.vectorstore.qdrant.initialize-schema=false");
    }
    void run(ApplicationContextRunner runner,Consumer<org.springframework.boot.test.context.assertj.AssertableApplicationContext> check) {
        runner.run(c->{
            assertThat(c).hasNotFailed().hasSingleBean(OpenAiChatModel.class).hasSingleBean(OpenAiEmbeddingModel.class);
            assertThat(org.springframework.test.util.ReflectionTestUtils.getField(c.getBean(RagService.class),"chatModel")).isSameAs(c.getBean(ChatModel.class));
            assertThat(org.springframework.test.util.ReflectionTestUtils.getField(c.getBean(AnswerabilityService.class),"chatModel")).isSameAs(c.getBean(ChatModel.class));
            try {check.accept(c);} finally {
                fixture.closeClients(c.getBean(OpenAiEmbeddingModel.class));
            }
        });
    }
    @ParameterizedTest @CsvSource({"false,false","true,false","false,true","true,true"})
    void timeoutSurvivesAllFourFlagCombinations(boolean lua,boolean resilience) {
        run(context(lua,resilience,"200ms"),c->{
            // Warm transport/class loading, then measure the real delayed HTTP call.
            c.getBean(ChatModel.class).call(new Prompt("warmup"));
            fixture.chatCalls.set(0);fixture.firstDelay=1000;
            // Separate options must not restore their builder's implicit 60s.
            var options=OpenAiChatOptions.builder().model("synthetic-chat").build();
            fixture.expectTimeout(()->c.getBean(ChatModel.class).call(new Prompt("synthetic",options)));
            assertThat(fixture.chatCalls).hasValue(1);
            assertThat(options.getTimeout()).isEqualTo(Duration.ofSeconds(60));
        });
    }
    @ParameterizedTest @CsvSource({"false,false","true,false","false,true","true,true"})
    void actualRagCitationAndRedisCacheSurviveAllFourFlagCombinations(boolean lua,boolean resilience) {
        fixture.ragReplies=true;fixture.firstDelay=100;fixture.laterDelay=100;
        run(context(lua,resilience,"2s"),c->{
            var cache=c.getBean(QueryEmbeddingCache.class);
            assertThat(cache.embed("synthetic query")).containsExactly(1,0,0);
            assertThat(cache.embed("synthetic query")).containsExactly(1,0,0);
            assertThat(fixture.embeddingCalls).hasValue(1);assertThat(cache.stats().hits()).isEqualTo(1);
            var response=c.getBean(RagService.class).ask("synthetic");
            assertThat(response.getAnswer()).contains("[S1]");assertThat(response.getSources()).hasSize(1);
            assertThat(response.getSources().getFirst().getDocumentId()).isEqualTo(100L);
            assertThat(response.getSources().getFirst().getSourceType()).isEqualTo("PERSONAL");
            assertThat(fixture.chatCalls).hasValue(2);
            if(resilience) {
                var guard=c.getBean(AiRemoteGuard.class);guard.breaker("queryEmbedding").transitionToOpenState();
                assertThat(cache.embed("synthetic query")).containsExactly(1,0,0);
                assertThatThrownBy(()->cache.embed("another query")).isInstanceOf(ProtectionException.class);
                assertThat(fixture.embeddingCalls).hasValue(1);
                assertThat(guard.breaker("deepseekChat").getMetrics().getNumberOfSuccessfulCalls()).isEqualTo(2);
            }
        });
    }
    @ParameterizedTest @CsvSource({"false,false","true,false","false,true","true,true"})
    void sdkRetriesRemainZeroAcrossAllFourFlagCombinations(boolean lua,boolean resilience) {
        fixture.chatStatus=503;
        run(context(lua,resilience,"2s"),c->{
            assertThatThrownBy(()->c.getBean(ChatModel.class).call(new Prompt("synthetic"))).isInstanceOf(com.openai.errors.OpenAIServiceException.class);
            assertThat(fixture.chatCalls).hasValue(1);
            if(resilience) {
                assertThatThrownBy(()->c.getBean(ChatModel.class).call(new Prompt("synthetic"))).isInstanceOf(com.openai.errors.OpenAIServiceException.class);
                assertThatThrownBy(()->c.getBean(ChatModel.class).call(new Prompt("synthetic")))
                        .isInstanceOfSatisfying(ProtectionException.class,e->assertThat(e.code()).isEqualTo("AI_CIRCUIT_OPEN"));
                assertThat(fixture.chatCalls).hasValue(2);
                assertThat(c.getBean(AiRemoteGuard.class).breaker("queryEmbedding").getMetrics().getNumberOfFailedCalls()).isZero();
            }
        });
    }
    @ParameterizedTest @CsvSource({"false,false","true,false","false,true","true,true"})
    void generationAlsoUsesAuthoritativeTimeout(boolean lua,boolean resilience) {
        fixture.ragReplies=true;fixture.laterDelay=1000;
        run(context(lua,resilience,"200ms"),c->{
            assertThatThrownBy(()->c.getBean(RagService.class).ask("synthetic"))
                    .isInstanceOfSatisfying(com.yu.transferrag.exception.AiServiceUnavailableException.class,
                            e->assertThat(e.getStage()).isEqualTo(com.yu.transferrag.exception.AiServiceUnavailableException.Stage.GENERATION));
            assertThat(fixture.chatCalls).hasValue(2);
        });
    }
    @Test void productionFortySecondsNotOverriddenByDefaultProtectionCeiling() {
        run(context(false,true,"40s"),c->assertThat(((OpenAiChatOptions)c.getBean(ChatModel.class).getDefaultOptions()).getTimeout()).isEqualTo(Duration.ofSeconds(40)));
        run(context(false,true,"40s").withPropertyValues("app.ai.resilience.chat-timeout=2s"),c->
                assertThat(((OpenAiChatOptions)c.getBean(ChatModel.class).getDefaultOptions()).getTimeout()).isEqualTo(Duration.ofSeconds(2)));
    }
    @Test void productionEnvironmentShapeStartsWithoutHmacSecretAndKeepsChatOverrides() {
        run(context(false,false,"40s").withInitializer(c->c.getEnvironment().getPropertySources().addFirst(
                new org.springframework.core.env.SystemEnvironmentPropertySource("synthetic-production-shape",java.util.Map.of(
                        "AI_CHAT_MODEL","deepseek-v4-pro","SPRING_AI_OPENAI_CHAT_TIMEOUT","40s",
                        "SPRING_AI_OPENAI_CHAT_MAX_RETRIES","0","APP_CACHE_EMBEDDING_ENABLED","true",
                        "APP_IMPORT_TASKS_ENABLED","false","APP_RAG_RATE_LIMIT_ENABLED","false","APP_AI_RESILIENCE_ENABLED","false"))))
                .withPropertyValues("spring.ai.openai.chat.model=${AI_CHAT_MODEL}"),c->{
            assertThat(c).doesNotHaveBean(RagRateLimiter.class);
            var options=(OpenAiChatOptions)c.getBean(ChatModel.class).getDefaultOptions();
            assertThat(options.getModel()).isEqualTo("deepseek-v4-pro");assertThat(options.getTimeout()).isEqualTo(Duration.ofSeconds(40));
            assertThat(c.getBean(org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties.class).getMaxRetries()).isZero();
        });
    }
    @Test void validBusinessRefusalIsNotCircuitFailure() {
        fixture.ragRefusal=true;
        run(context(false,true,"2s"),c->{
            var answer=c.getBean(RagService.class).ask("synthetic");assertThat(answer.getSources()).isEmpty();
            assertThat(fixture.chatCalls).hasValue(1);
            var metrics=c.getBean(AiRemoteGuard.class).breaker("deepseekChat").getMetrics();
            assertThat(metrics.getNumberOfSuccessfulCalls()).isEqualTo(1);assertThat(metrics.getNumberOfFailedCalls()).isZero();
        });
    }
    @ParameterizedTest @CsvSource({"false","true"})
    void bulkheadRejectsWhileActualHttpCallIsInFlight(boolean lua) {
        fixture.firstDelay=1000;
        run(context(lua,true,"2s").withPropertyValues("app.ai.resilience.chat-concurrency=1"),c->{
            var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
            try {
                var first=pool.submit(()->c.getBean(ChatModel.class).call(new Prompt("synthetic")));
                long until=System.nanoTime()+java.util.concurrent.TimeUnit.SECONDS.toNanos(3);
                while(fixture.chatCalls.get()==0 && System.nanoTime()<until) Thread.sleep(5);
                assertThat(fixture.chatCalls).hasValue(1);
                assertThatThrownBy(()->c.getBean(ChatModel.class).call(new Prompt("synthetic")))
                        .isInstanceOfSatisfying(ProtectionException.class,e->assertThat(e.code()).isEqualTo("AI_BUSY"));
                assertThat(first.get(3,java.util.concurrent.TimeUnit.SECONDS)).isNotNull();assertThat(fixture.chatCalls).hasValue(1);
            }catch(Exception e){throw new AssertionError(e);}finally{pool.shutdownNow();}
        });
    }
    @Test void realLua429StopsBeforeEmbeddingAndChat() {
        run(context(true,true,"2s"),c->{
            var limiter=c.getBean(RagRateLimiter.class);var p=c.getBean(RateLimitProperties.class);
            var request=new org.springframework.mock.web.MockHttpServletRequest();request.setRemoteAddr("127.0.0.1");
            limiter.check(ClientIdentity.hash(request,p));
            HandlerInterceptor gate=new HandlerInterceptor(){@Override public boolean preHandle(jakarta.servlet.http.HttpServletRequest r,jakarta.servlet.http.HttpServletResponse response,Object h){limiter.check(ClientIdentity.hash(r,p));return true;}};
            var mvc=MockMvcBuilders.standaloneSetup(new RagController(c.getBean(RagService.class)))
                    .setControllerAdvice(new GlobalExceptionHandler()).addInterceptors(gate).build();
            try {mvc.perform(post("/api/rag/ask").contentType("application/json").content("{\"question\":\"synthetic\"}"))
                    .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After")).andExpect(jsonPath("$.code").value("RATE_LIMITED"));}
            catch(Exception e){throw new AssertionError(e);}
            assertThat(fixture.chatCalls).hasValue(0);assertThat(fixture.embeddingCalls).hasValue(0);
            verifyNoInteractions(c.getBean(RetrievalService.class));
        });
    }
    @Test void redisFailureIsClosedForLimiterButOpenForEmbeddingCache() {
        // Wrong port only in isolated context; never stop a Redis instance or change its password.
        run(context(true,true,"2s").withPropertyValues("app.cache.embedding.redis.port=1","app.cache.embedding.redis.timeout=50ms"),c->{
            assertThatThrownBy(()->c.getBean(RagRateLimiter.class).check("isolated"))
                    .isInstanceOfSatisfying(ProtectionException.class,e->assertThat(e.code()).isEqualTo("RATE_LIMITER_UNAVAILABLE"));
            assertThat(c.getBean(QueryEmbeddingCache.class).embed("synthetic")).containsExactly(1,0,0);
            assertThat(fixture.embeddingCalls).hasValue(1);assertThat(c.getBean(QueryEmbeddingCache.class).stats().errors()).isPositive();
        });
    }
}
