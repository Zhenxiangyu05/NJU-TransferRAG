package com.yu.transferrag.config;

import com.sun.net.httpserver.HttpServer;
import com.yu.transferrag.cache.*;
import com.yu.transferrag.dto.SearchResultResponse;
import com.yu.transferrag.entity.Document;
import com.yu.transferrag.entity.DocumentRole;
import com.yu.transferrag.exception.AiServiceUnavailableException;
import com.yu.transferrag.repository.DocumentRepository;
import com.yu.transferrag.repository.EvidenceRefRepository;
import com.yu.transferrag.service.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.openai.autoconfigure.*;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.*;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real auto-configured SDK + loopback HTTP, no external credentials or provider calls. */
class ChatTimeoutConfigurationTest {
    HttpServer server;
    ExecutorService executor;
    AtomicInteger chatCalls = new AtomicInteger(), embeddingCalls = new AtomicInteger();
    volatile long firstDelay, laterDelay, embeddingDelay;
    volatile int chatStatus = 200, embeddingStatus = 200;
    volatile boolean retryAfter, ragReplies, ragRefusal;
    volatile String receivedBody;

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newFixedThreadPool(4);
        server.setExecutor(executor);
        server.createContext("/v1/", exchange -> {
            boolean chat = exchange.getRequestURI().getPath().endsWith("/chat/completions");
            int number = (chat ? chatCalls : embeddingCalls).incrementAndGet();
            String request = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            if (chat) receivedBody = request; // Synthetic fixture only; never printed.
            try {
                Thread.sleep(chat ? (number == 1 ? firstDelay : laterDelay) : embeddingDelay);
                int status = chat ? chatStatus : embeddingStatus;
                String content = ragRefusal ? "{\"answerable\":false,\"evidenceCitationIds\":[],\"reason\":\"insufficient fixture\"}"
                        : ragReplies && number == 1
                        ? "{\"answerable\":true,\"evidenceCitationIds\":[\"S1\"],\"reason\":\"fixture\"}"
                        : "Synthetic answer [S1].";
                String body = status >= 400
                        ? "{\"error\":{\"type\":\"server_error\",\"message\":\"local fixture\"}}"
                        : chat ? "{\"id\":\"local\",\"object\":\"chat.completion\",\"created\":1,\"model\":\"synthetic-chat\",\"choices\":[{\"index\":0,\"message\":{\"role\":\"assistant\",\"content\":"
                        + new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(content)
                        + "},\"finish_reason\":\"stop\"}],\"usage\":{\"prompt_tokens\":1,\"completion_tokens\":1,\"total_tokens\":2}}"
                        : "{\"object\":\"list\",\"model\":\"synthetic-embedding\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":[1,0,0]}],\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}";
                byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                if (retryAfter && chat) exchange.getResponseHeaders().set("Retry-After", "0");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } catch (java.io.IOException expectedOnTimeout) {
                // The client closes a delayed response when its real HTTP timeout expires.
            } finally { exchange.close(); }
        });
        server.start();
    }

    @AfterEach void stop() { server.stop(0); executor.shutdownNow(); }

    ApplicationContextRunner runner(String... values) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OpenAiChatAutoConfiguration.class, OpenAiEmbeddingAutoConfiguration.class))
                .withUserConfiguration(ChatTimeoutConfiguration.class)
                .withBean(ToolCallingManager.class, () -> mock(ToolCallingManager.class))
                .withPropertyValues("spring.ai.openai.base-url=http://127.0.0.1:" + server.getAddress().getPort() + "/v1",
                        "spring.ai.openai.api-key=synthetic-local-token", "spring.ai.openai.chat.model=synthetic-chat",
                        "spring.ai.openai.embedding.model=synthetic-embedding", "spring.ai.openai.embedding.dimensions=3",
                        "spring.ai.openai.chat.max-retries=0")
                .withPropertyValues(values);
    }

    void run(ApplicationContextRunner runner, Consumer<org.springframework.boot.test.context.assertj.AssertableApplicationContext> check) {
        runner.run(c -> {
            assertThat(c).hasNotFailed().hasSingleBean(OpenAiChatModel.class);
            assertThat(c.getBean(ChatModel.class)).isInstanceOf(ChatTimeoutConfiguration.TimeoutConfiguredChatModel.class);
            if (c.containsBean("ragService")) {
                assertThat(org.springframework.test.util.ReflectionTestUtils.getField(c.getBean(RagService.class), "chatModel"))
                        .isSameAs(c.getBean(ChatModel.class));
                assertThat(org.springframework.test.util.ReflectionTestUtils.getField(c.getBean(AnswerabilityService.class), "chatModel"))
                        .isSameAs(c.getBean(ChatModel.class));
            }
            try { check.accept(c); }
            finally {
                // Auto-configured model ownership is unchanged. Release SDK pools in this short-lived fixture.
                closeClients(c.getBean(OpenAiEmbeddingModel.class));
            }
        });
    }

    void closeClients(Object model) throws Exception {
        for (String field : List.of("openAiClient", "openAiClientAsync")) {
            try {
                var f = model.getClass().getDeclaredField(field); f.setAccessible(true);
                Object client = f.get(model);
                if (client instanceof com.openai.client.OpenAIClient sync) sync.close();
                else if (client instanceof com.openai.client.OpenAIClientAsync async) async.close();
            } catch (NoSuchFieldException ignored) { }
        }
    }

    void expectTimeout(Runnable operation) {
        long start = System.nanoTime();
        assertThatThrownBy(operation::run).isInstanceOfAny(com.openai.errors.OpenAIIoException.class, AiServiceUnavailableException.class);
        long millis = (System.nanoTime() - start) / 1_000_000;
        assertThat(millis).isBetween(130L, 850L);
        System.out.println("LOCAL_CHAT_TIMEOUT elapsed_ms=" + millis + " http_attempts=" + chatCalls.get());
    }

    @Test void configured200msReachesRealHttpRequest() {
        firstDelay = 1000;
        run(runner("spring.ai.openai.chat.timeout=200ms"), c -> {
            expectTimeout(() -> c.getBean(ChatModel.class).call(new Prompt("synthetic")));
            assertThat(chatCalls).hasValue(1);
        });
    }

    @Test void sdkClientsCloseExactlyOnceAndFacadeNeverOwnsThem() {
        var sync=mock(com.openai.client.OpenAIClient.class);
        var async=mock(com.openai.client.OpenAIClientAsync.class);
        var original=OpenAiChatModel.builder().openAiClient(sync).openAiClientAsync(async)
                .options(OpenAiChatOptions.builder().model("synthetic").build()).build();
        new ApplicationContextRunner().withUserConfiguration(ChatTimeoutConfiguration.class)
                .withBean("openAiChatModel",OpenAiChatModel.class,()->original)
                .withBean(OpenAiCommonProperties.class,OpenAiCommonProperties::new)
                .withBean(OpenAiChatProperties.class,OpenAiChatProperties::new)
                .run(c->{
                    var lifecycle=c.getBean("chatClientLifecycle",org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor.class);
                    assertThat(lifecycle.requiresDestruction(original)).isTrue();
                    assertThat(lifecycle.requiresDestruction(c.getBean(ChatModel.class))).isFalse();
                    lifecycle.postProcessBeforeDestruction(c.getBean(ChatModel.class),"timeoutConfiguredChatModel");
                    verifyNoInteractions(sync,async);
                    lifecycle.postProcessBeforeDestruction(original,"openAiChatModel");
                    lifecycle.postProcessBeforeDestruction(original,"openAiChatModel");
                });
        verify(sync,times(1)).close();verify(async,times(1)).close();
    }

    @Test void separatePromptOptionsCannotRestoreDefault60sAndAreNotMutated() {
        firstDelay = 1000;
        var options = OpenAiChatOptions.builder().model("synthetic-chat").temperature(0.2).seed(12).build();
        run(runner("spring.ai.openai.chat.timeout=200ms"), c -> {
            expectTimeout(() -> c.getBean(ChatModel.class).call(new Prompt("synthetic", options)));
            assertThat(options.getTimeout()).isEqualTo(Duration.ofSeconds(60));
            assertThat(receivedBody).contains("\"temperature\":0.2", "\"seed\":12");
            assertThat(chatCalls).hasValue(1);
        });
    }

    @Test void configured2sAllows100msSuccessfulResponse() {
        firstDelay = 100;
        run(runner("spring.ai.openai.chat.timeout=2s"), c -> {
            assertThat(c.getBean(ChatModel.class).call(new Prompt("synthetic")).getResult().getOutput().getText()).contains("Synthetic answer");
            assertThat(chatCalls).hasValue(1);
        });
    }

    @Test void sdkZeroRetriesMakesOneAttemptForOrdinary503() {
        chatStatus = 503;
        run(runner(), c -> {
            assertThatThrownBy(() -> c.getBean(ChatModel.class).call(new Prompt("synthetic"))).isInstanceOf(com.openai.errors.OpenAIServiceException.class);
            assertThat(chatCalls).hasValue(1);
        });
    }

    @Test void okhttpFollowupRemainsDistinctFromSdkRetries() {
        chatStatus = 503; retryAfter = true;
        run(runner(), c -> {
            assertThatThrownBy(() -> c.getBean(ChatModel.class).call(new Prompt("synthetic"))).isInstanceOf(com.openai.errors.OpenAIServiceException.class);
            assertThat(chatCalls).hasValue(2);
            System.out.println("LOCAL_OKHTTP_FOLLOWUP sdk_retries=0 http_attempts=" + chatCalls.get());
        });
    }

    @Test void embeddingTimeoutAndCacheStayIndependent() {
        embeddingDelay = 450;
        run(runner("spring.ai.openai.timeout=1500ms", "spring.ai.openai.max-retries=1",
                "spring.ai.openai.chat.timeout=200ms"), c -> {
            var original = c.getBean(OpenAiEmbeddingModel.class);
            var store = mock(EmbeddingCacheStore.class);
            var bytes = new ConcurrentHashMap<String, byte[]>();
            when(store.get(anyString())).thenAnswer(i -> bytes.get(i.getArgument(0)));
            doAnswer(i -> { bytes.put(i.getArgument(0), i.getArgument(1)); return null; }).when(store).put(anyString(), any(), any());
            var properties = new EmbeddingCacheProperties(true, Duration.ofHours(24), 0.1, 3, "v1", "fixture", "exact", Duration.ZERO);
            var cache = new QueryEmbeddingCache(original, store, properties, () -> "fixture", new SimpleMeterRegistry());
            assertThat(cache.embed("synthetic")).containsExactly(1, 0, 0);
            assertThat(cache.embed("synthetic")).containsExactly(1, 0, 0);
            assertThat(embeddingCalls).hasValue(1);
            assertThat(cache.stats().hits()).isEqualTo(1);
            var resolved = OpenAiAutoConfigurationUtil.resolveCommonProperties(c.getBean(OpenAiCommonProperties.class), c.getBean(OpenAiEmbeddingProperties.class));
            assertThat(resolved.getTimeout()).isEqualTo(Duration.ofMillis(1500));
            assertThat(resolved.getMaxRetries()).isEqualTo(1);
        });
    }

    @Test void embeddingRetriesRemainIndependent() {
        embeddingStatus = 503; chatStatus = 503;
        run(runner("spring.ai.openai.max-retries=1", "spring.ai.openai.chat.timeout=200ms"), c -> {
            assertThatThrownBy(() -> c.getBean(ChatModel.class).call(new Prompt("synthetic"))).isInstanceOf(com.openai.errors.OpenAIServiceException.class);
            assertThatThrownBy(() -> c.getBean(OpenAiEmbeddingModel.class).embed("synthetic")).isInstanceOf(com.openai.errors.OpenAIServiceException.class);
            assertThat(chatCalls).hasValue(1);
            assertThat(embeddingCalls).hasValue(2);
        });
    }

    @Test void uppercaseEnvironmentKeysBindAuthoritativeTimeoutAndRetries() {
        run(runner().withInitializer(c -> c.getEnvironment().getPropertySources().addFirst(
                new SystemEnvironmentPropertySource("synthetic-env", Map.of("SPRING_AI_OPENAI_CHAT_MAX_RETRIES", "0",
                        "SPRING_AI_OPENAI_CHAT_TIMEOUT", "40s")))), c -> {
            assertThat(c.getBean(OpenAiChatProperties.class).getMaxRetries()).isZero();
            assertThat(((OpenAiChatOptions)c.getBean(ChatModel.class).getDefaultOptions()).getTimeout()).isEqualTo(Duration.ofSeconds(40));
        });
    }

    @Test void commonTimeoutFallbackAndLongerThan60sConfigArePreserved() {
        run(runner("spring.ai.openai.timeout=90s"), c -> {
            assertThat(((OpenAiChatOptions)c.getBean(ChatModel.class).getDefaultOptions()).getTimeout()).isEqualTo(Duration.ofSeconds(90));
            assertThat(c.getBean(ChatModel.class).call(new Prompt("synthetic")).getResult()).isNotNull();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @Import({AnswerabilityService.class, RagService.class})
    static class RagFixture {
        @Bean RetrievalService retrieval() {
            var result = new SearchResultResponse(); result.setDocumentId(100L); result.setChunkId(200L);
            result.setChunkIndex(0); result.setDocumentRole(DocumentRole.EVIDENCE); result.setScore(0.99);
            result.setContent("Synthetic fixture fact.");
            var retrieval = mock(RetrievalService.class);
            when(retrieval.search(anyString(), eq(3))).thenReturn(List.of(result)); return retrieval;
        }
        @Bean DocumentRepository documents() {
            var repository = mock(DocumentRepository.class); var document = new Document();
            document.setId(100L); document.setTitle("Synthetic fixture"); document.setSourceType("PERSONAL"); document.setDocumentRole(DocumentRole.EVIDENCE);
            when(repository.findAllById(any())).thenReturn(List.of(document)); return repository;
        }
        @Bean EvidenceRefRepository refs() { return mock(EvidenceRefRepository.class); }
        @Bean EvidenceReadinessService readiness() { return PublishedEvidenceFixture.gate(); }
    }

    @Test void actualInjectedAnswerabilityTimesOutBeforeGeneration() {
        ragReplies = true; firstDelay = 1000;
        run(runner("spring.ai.openai.chat.timeout=200ms").withUserConfiguration(RagFixture.class), c -> {
            assertThatThrownBy(() -> c.getBean(RagService.class).ask("synthetic"))
                    .isInstanceOfSatisfying(AiServiceUnavailableException.class,
                            e -> assertThat(e.getStage()).isEqualTo(AiServiceUnavailableException.Stage.ANSWERABILITY));
            assertThat(chatCalls).hasValue(1);
        });
    }

    @Test void actualInjectedGenerationTimesOutAfterSuccessfulAnswerability() {
        ragReplies = true; laterDelay = 1000;
        run(runner("spring.ai.openai.chat.timeout=200ms").withUserConfiguration(RagFixture.class), c -> {
            assertThatThrownBy(() -> c.getBean(RagService.class).ask("synthetic"))
                    .isInstanceOfSatisfying(AiServiceUnavailableException.class,
                            e -> assertThat(e.getStage()).isEqualTo(AiServiceUnavailableException.Stage.GENERATION));
            assertThat(chatCalls).hasValue(2);
        });
    }

    @Test void actualInjectedAnswerabilityAndGenerationSucceedWithCitation() {
        ragReplies = true; firstDelay = 100; laterDelay = 100;
        run(runner("spring.ai.openai.chat.timeout=2s").withUserConfiguration(RagFixture.class), c -> {
            var response = c.getBean(RagService.class).ask("synthetic");
            assertThat(response.getAnswer()).contains("[S1]");
            assertThat(response.getSources()).hasSize(1);
            assertThat(response.getSources().getFirst().getDocumentId()).isEqualTo(100L);
            assertThat(chatCalls).hasValue(2);
        });
    }
}
