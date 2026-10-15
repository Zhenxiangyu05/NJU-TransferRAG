package com.yu.transferrag.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import com.yu.transferrag.cache.*;
import com.yu.transferrag.dto.SearchResultResponse;
import com.yu.transferrag.entity.*;
import com.yu.transferrag.protection.*;
import com.yu.transferrag.repository.*;
import com.yu.transferrag.service.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.openai.autoconfigure.*;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.*;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.support.ResourcePropertySource;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Two distinct loopback providers; only synthetic credentials/data ever enter these fixtures. */
class ChatProviderIsolationTest {
    static final ObjectMapper JSON = new ObjectMapper();
    static final String SCHOOL_KEY = "synthetic-school-only", OFFICIAL_KEY = "synthetic-official-only";
    Stub official, school;

    record Request(String path, String auth, JsonNode body) {}
    static class Stub implements AutoCloseable {
        final HttpServer server;
        final ExecutorService pool = Executors.newFixedThreadPool(4);
        final List<Request> requests = new CopyOnWriteArrayList<>();
        volatile int status = 200;
        volatile long delay;
        volatile boolean refuse;
        volatile boolean disconnect;
        Stub() throws Exception {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(pool);
            server.createContext("/", x -> {
                var body = JSON.readTree(x.getRequestBody());
                String path = x.getRequestURI().getPath();
                requests.add(new Request(path, x.getRequestHeaders().getFirst("Authorization"), body));
                try {
                    if (disconnect) return;
                    Thread.sleep(delay);
                    Map<String, Object> response;
                    if (status != 200) response = Map.of("error", Map.of("type", "server_error", "message", "fixture"));
                    else if (path.endsWith("/embeddings")) {
                        int count = body.get("input").isArray() ? body.get("input").size() : 1;
                        var data = new ArrayList<Map<String, Object>>();
                        for (int n = 0; n < count; n++) data.add(Map.of("object", "embedding", "index", n,
                                "embedding", Collections.nCopies(1024, 0.25)));
                        response = Map.of("object", "list", "model", "bge-m3", "data", data,
                                "usage", Map.of("prompt_tokens", 1, "total_tokens", 1));
                    } else {
                        boolean check = body.path("messages").toString().contains("evidenceCitationIds");
                        String content = check ? JSON.writeValueAsString(Map.of("answerable", !refuse,
                                "evidenceCitationIds", refuse ? List.of() : List.of("S1"), "reason", "synthetic"))
                                : "Synthetic supported fact. [S1]";
                        response = Map.of("id", "synthetic", "object", "chat.completion", "created", 1,
                                "model", body.path("model").asText(), "choices", List.of(Map.of("index", 0,
                                "message", Map.of("role", "assistant", "content", content), "finish_reason", "stop")),
                                "usage", Map.of("prompt_tokens", 1, "completion_tokens", 1, "total_tokens", 2));
                    }
                    byte[] bytes = JSON.writeValueAsBytes(response);
                    x.getResponseHeaders().set("Content-Type", "application/json");
                    x.sendResponseHeaders(status, bytes.length);
                    x.getResponseBody().write(bytes);
                } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                catch (java.io.IOException clientTimedOut) { /* Expected for deadline fixtures. */ }
                finally { x.close(); }
            });
            server.start();
        }
        String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
        public void close() { server.stop(0); pool.shutdownNow(); }
    }

    @BeforeEach void start() throws Exception { official = new Stub(); school = new Stub(); }
    @AfterEach void close() { official.close(); school.close(); }

    ApplicationContextRunner context(String... overrides) {
        return new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(OpenAiChatAutoConfiguration.class, OpenAiEmbeddingAutoConfiguration.class))
                .withUserConfiguration(ChatTimeoutConfiguration.class, AiResilienceConfiguration.class)
                .withBean(ToolCallingManager.class, () -> mock(ToolCallingManager.class))
                .withInitializer(c -> {
                    c.getEnvironment().getPropertySources().remove(org.springframework.core.env.StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    try { c.getEnvironment().getPropertySources().addLast(new ResourcePropertySource("classpath:application.properties")); }
                    catch (java.io.IOException e) { throw new IllegalStateException(e); }
                    c.getEnvironment().getPropertySources().addLast(new SystemEnvironmentPropertySource("fixture-env", Map.of(
                            "AI_BASE_URL", school.url() + "/v1", "AI_API_KEY", SCHOOL_KEY,
                            "AI_CHAT_MODEL", "legacy-school-chat", "AI_EMBEDDING_MODEL", "bge-m3",
                            "AI_CHAT_PROVIDER", "deepseek-official", "DEEPSEEK_API_KEY", OFFICIAL_KEY)));
                })
                .withPropertyValues("app.ai.chat.deepseek.base-url=" + official.url(),
                        "app.ai.chat.deepseek.model=deepseek-flash", "spring.ai.openai.timeout=2s",
                        "app.ai.resilience.enabled=true", "app.ai.resilience.embedding-timeout=200ms",
                        "app.ai.resilience.extra-retries=0")
                .withPropertyValues(overrides);
    }

    void run(ApplicationContextRunner runner, java.util.function.Consumer<org.springframework.boot.test.context.assertj.AssertableApplicationContext> check) {
        runner.run(c -> {
            assertThat(c).hasNotFailed();
            try { check.accept(c); }
            finally { closeEmbedding(c.getBean(OpenAiEmbeddingModel.class)); }
        });
    }
    static void closeEmbedding(OpenAiEmbeddingModel model) {
        try {
            var f = OpenAiEmbeddingModel.class.getDeclaredField("openAiClient"); f.setAccessible(true);
            ((com.openai.client.OpenAIClient) f.get(model)).close();
        } catch (ReflectiveOperationException e) { throw new AssertionError(e); }
    }

    @ParameterizedTest @ValueSource(booleans = {true, false})
    void distinctProvidersKeysAndOriginal1024ProtocolSurviveCacheAndImport(boolean protectedQuery) {
        run(context("app.ai.resilience.enabled=" + protectedQuery), c -> {
            var chat = c.getBean(ChatModel.class);
            assertThat(chat.call(new Prompt("synthetic" )).getResult().getOutput().getText()).contains("[S1]");
            var original = c.getBean(OpenAiEmbeddingModel.class);
            var bytes = new ConcurrentHashMap<String, byte[]>();
            var store = mock(EmbeddingCacheStore.class);
            when(store.get(anyString())).thenAnswer(i -> bytes.get(i.getArgument(0)));
            doAnswer(i -> { bytes.put(i.getArgument(0), i.getArgument(1)); return null; }).when(store).put(anyString(), any(), any());
            var cache = new QueryEmbeddingCache(original, store,
                    new EmbeddingCacheProperties(true, Duration.ofHours(24), 0.1, 1024, "v1", "fixture", "exact", Duration.ZERO),
                    () -> "unchanged-school-signature", new SimpleMeterRegistry());
            cache.setRemoteGuard(c.getBean(AiRemoteGuard.class));
            var first = cache.embed("synthetic query");
            assertThat(cache.embed("synthetic query")).containsExactly(first).hasSize(1024);
            assertThat(cache.stats().hits()).isEqualTo(1);
            assertThat(cache.stats().misses()).isEqualTo(1);
            assertThat(cache.stats().embeddingCalls()).isEqualTo(1);
            assertThat(school.requests).hasSize(1);
            if (protectedQuery) {
                c.getBean(AiRemoteGuard.class).breaker("queryEmbedding").transitionToOpenState();
                assertThat(cache.embed("synthetic query")).containsExactly(first);
                assertThatThrownBy(() -> cache.embed("different query")).isInstanceOf(ProtectionException.class);
                assertThat(school.requests).hasSize(1);
            }
            // Actual asynchronous import indexer invokes the original Spring bean, not the query facade.
            var snapshots = mock(EvidenceSnapshotStateService.class);
            var input = new EvidenceSnapshotCodec.Input(1, "isolated", "bge-m3", List.of(
                    new EvidenceSnapshotCodec.TextPoint(UUID.randomUUID().toString(), "synthetic import", Map.of("chunkId", "1"))));
            when(snapshots.load(any())).thenReturn(new EvidenceSnapshotStateService.Frozen(input, null));
            when(snapshots.freeze(any(), any())).thenAnswer(i -> i.getArgument(1));
            // Longer than the query timeout, still below unchanged original timeout.
            school.delay = 450;
            var indexer = new FrozenEvidenceIndexer(snapshots, original, mock(VectorStore.class), "isolated", "bge-m3") {
                @Override protected void write(EvidenceSnapshotCodec.Wire wire) { assertThat(wire.dimension()).isEqualTo(1024); }
            };
            assertThat(indexer.index(new ImportTaskStateService.Lease(1, 1, "fixture", 1, ImportTaskStage.INDEXING))).isEqualTo(1);
            assertThat(school.requests).hasSize(2);
            for (Request r : school.requests) {
                assertThat(r.path()).isEqualTo("/v1/embeddings");
                assertThat(r.auth()).isEqualTo("Bearer " + SCHOOL_KEY).doesNotContain(OFFICIAL_KEY);
                assertThat(r.body().path("model").asText()).isEqualTo("bge-m3");
                assertThat(r.body().has("thinking")).isFalse();
                assertThat(r.body().has("dimensions")).isFalse(); // Do not change the school's existing request options.
            }
            assertThat(official.requests).hasSize(1);
            assertOfficial(official.requests.getFirst());
        });
    }

    void assertOfficial(Request r) {
        assertThat(r.path()).isEqualTo("/chat/completions");
        assertThat(java.util.Objects.equals(r.auth(), "Bearer " + OFFICIAL_KEY)).as("official synthetic authentication identity").isTrue();
        assertThat(r.body().path("model").asText()).isEqualTo("deepseek-flash");
        assertThat(r.body().path("thinking").path("type").asText()).isEqualTo("disabled");
    }

    @Test void legacyRollbackUsesSchoolOnlyWithoutOfficialParameters() {
        run(context("app.ai.chat.provider=njusehub"), c -> {
            c.getBean(ChatModel.class).call(new Prompt("synthetic"));
            c.getBean(OpenAiEmbeddingModel.class).embed("synthetic");
            assertThat(official.requests).isEmpty();
            assertThat(school.requests).hasSize(2);
            assertThat(school.requests.getFirst().path()).isEqualTo("/v1/chat/completions");
            assertThat(school.requests.getFirst().body().path("model").asText()).isEqualTo("legacy-school-chat");
            assertThat(school.requests.getFirst().body().has("thinking")).isFalse();
            assertThat(school.requests).allSatisfy(r -> assertThat(r.auth()).isEqualTo("Bearer " + SCHOOL_KEY));
        });
    }

    @Test void officialPromptOptionsCannotRestoreThinkingRetriesOrAnotherModel() {
        run(context(), c -> {
            var supplied = OpenAiChatOptions.builder().model("wrong-model").maxRetries(9)
                    .extraBody(Map.of("thinking", Map.of("type", "enabled"))).build();
            c.getBean(ChatModel.class).call(new Prompt("synthetic", supplied));
            assertOfficial(official.requests.getFirst());
            assertThat(supplied.getMaxRetries()).isEqualTo(9);
            var defaults = (OpenAiChatOptions) c.getBean(ChatModel.class).getDefaultOptions();
            assertThat(defaults.getTimeout()).isEqualTo(Duration.ofSeconds(40));
            assertThat(defaults.getMaxRetries()).isZero();
        });
    }

    @Test void promptCredentialsCannotCrossProviderBoundary() {
        run(context(), c -> {
            assertThatThrownBy(() -> c.getBean(ChatModel.class).call(new Prompt("synthetic",
                    OpenAiChatOptions.builder().baseUrl(school.url()).apiKey(OFFICIAL_KEY).build())))
                    .isInstanceOf(IllegalArgumentException.class);
            assertThat(school.requests).isEmpty(); assertThat(official.requests).isEmpty();
        });
    }

    @ParameterizedTest @ValueSource(strings = {"spring.ai.openai.embedding.base-url=https://api.deepseek.com",
            "spring.ai.openai.embedding.api-key=synthetic-official-only", "spring.ai.openai.embedding.model=other",
            "spring.ai.openai.embedding.dimensions=3", "DEEPSEEK_API_KEY=", "app.ai.chat.deepseek.base-url=https://example.com",
            "spring.ai.openai.custom-headers.Authorization=synthetic-school-only"})
    void invalidIsolationFailsAtStartupBeforeAnyNetwork(String setting) {
        context(setting).run(c -> assertThat(c).hasFailed());
        assertThat(school.requests).isEmpty(); assertThat(official.requests).isEmpty();
    }

    @Test void officialChatRealTimeoutAndSdkRetriesRemainControlled() {
        run(context("spring.ai.openai.chat.timeout=200ms"), c -> {
            c.getBean(ChatModel.class).call(new Prompt("warmup"));
            official.requests.clear(); official.delay = 1000;
            long start = System.nanoTime();
            assertThatThrownBy(() -> c.getBean(ChatModel.class).call(new Prompt("synthetic")))
                    .isInstanceOf(com.openai.errors.OpenAIIoException.class);
            assertThat(Duration.ofNanos(System.nanoTime() - start).toMillis()).isBetween(120L, 900L);
            assertThat(official.requests).hasSize(1);
            official.delay = 0; official.status = 503; official.requests.clear();
            assertThatThrownBy(() -> c.getBean(ChatModel.class).call(new Prompt("synthetic")))
                    .isInstanceOf(com.openai.errors.OpenAIServiceException.class);
            assertThat(official.requests).hasSize(1);
            assertThat(school.requests).isEmpty();
        });
    }

    @Test void actualFortySecondHttpDeadlineSurvivesOfficialProviderAndProtection() {
        run(context("spring.ai.openai.chat.timeout=40s"), c -> {
            var model = c.getBean(ChatModel.class);
            model.call(new Prompt("synthetic warmup"));
            official.requests.clear(); official.delay = 45000;
            long start = System.nanoTime();
            assertThatThrownBy(() -> model.call(new Prompt("synthetic deadline fixture")))
                    .isInstanceOf(com.openai.errors.OpenAIIoException.class);
            long millis = Duration.ofNanos(System.nanoTime() - start).toMillis();
            assertThat(millis).isBetween(39000L, 43000L);
            assertThat(official.requests).hasSize(1);
            assertThat(school.requests).isEmpty();
            System.out.println("OFFICIAL_CHAT_LOCAL_40S_DEADLINE elapsed_ms=" + millis + " http_attempts=1");
        });
    }

    @Test void officialProModelAndDefaultOfficialEndpointBindIndependently() {
        run(context("app.ai.chat.deepseek.model=deepseek-v4-pro"), c -> {
            c.getBean(ChatModel.class).call(new Prompt("synthetic"));
            assertThat(official.requests.getFirst().body().path("model").asText()).isEqualTo("deepseek-v4-pro");
            assertThat(official.requests.getFirst().body().path("thinking").path("type").asText()).isEqualTo("disabled");
        });
        run(context("app.ai.chat.deepseek.base-url=https://api.deepseek.com"), c -> {
            var chat = c.getBean(OpenAiChatProperties.class);
            var embedding = c.getBean(OpenAiEmbeddingProperties.class);
            assertThat(chat.getBaseUrl()).isEqualTo("https://api.deepseek.com");
            assertThat(embedding.getBaseUrl()).isEqualTo(school.url() + "/v1");
        }); // Property verification only; no real provider request.
    }

    @Test void officialChatCircuitOpensWithoutAffectingSchoolEmbedding() {
        official.status = 503;
        run(context("app.ai.resilience.minimum-calls=2", "app.ai.resilience.window-size=4"), c -> {
            var model = c.getBean(ChatModel.class);
            for (int n = 0; n < 2; n++) assertThatThrownBy(() -> model.call(new Prompt("synthetic")))
                    .isInstanceOf(com.openai.errors.OpenAIServiceException.class);
            assertThatThrownBy(() -> model.call(new Prompt("synthetic"))).isInstanceOfSatisfying(ProtectionException.class,
                    e -> assertThat(e.code()).isEqualTo("AI_CIRCUIT_OPEN"));
            assertThat(official.requests).hasSize(2);
            assertThat(c.getBean(AiRemoteGuard.class).embed(c.getBean(OpenAiEmbeddingModel.class), "synthetic query")).hasSize(1024);
            assertThat(school.requests).hasSize(1);
        });
    }

    @Test void officialChatBulkheadRejectsFastAndReleasesPermit() {
        run(context("app.ai.resilience.chat-concurrency=1"), c -> {
            official.delay = 400;
            var pool = Executors.newSingleThreadExecutor();
            var model = c.getBean(ChatModel.class);
            try {
                var first = pool.submit(() -> model.call(new Prompt("synthetic")));
                long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
                while (official.requests.isEmpty() && System.nanoTime() < end) Thread.sleep(5);
                assertThat(official.requests).hasSize(1);
                assertThatThrownBy(() -> model.call(new Prompt("synthetic"))).isInstanceOfSatisfying(ProtectionException.class,
                        e -> assertThat(e.code()).isEqualTo("AI_BUSY"));
                assertThat(first.get(3, TimeUnit.SECONDS).getResult()).isNotNull();
                official.delay = 0;
                assertThat(model.call(new Prompt("synthetic")).getResult()).isNotNull();
                assertThat(official.requests).hasSize(2);
            } catch (Exception e) { throw new AssertionError(e); }
            finally { pool.shutdownNow(); }
        });
    }

    @ParameterizedTest @ValueSource(strings = {"canonical", "fallback", "refusal"})
    void actualOfficialChatBeanSupportsCanonicalEvidenceAndSafeRefusal(String mode) {
        run(context(), c -> {
            var model = c.getBean(ChatModel.class);
            var retrieval = mock(RetrievalService.class);
            var prepared = mock(RetrievalService.PreparedQuery.class);
            when(retrieval.prepareCanonicalFirst(anyString())).thenReturn(prepared);
            var evidence = new Document(); evidence.setId(100L); evidence.setTitle("Synthetic evidence");
            evidence.setDocumentRole(DocumentRole.EVIDENCE); evidence.setSourceType("PERSONAL");
            var repos = mock(DocumentRepository.class); when(repos.findAllById(any())).thenReturn(List.of(evidence));
            var refs = mock(EvidenceRefRepository.class);
            if (mode.equals("canonical")) {
                var candidate = candidate(900, 99, DocumentRole.CANONICAL, "[F0] Synthetic supported fact.");
                when(retrieval.searchCanonical(prepared, 3)).thenReturn(List.of(candidate));
                var chunk = new Chunk(); chunk.setId(900L);
                var ref = new EvidenceRef(); ref.setId(1L); ref.setCanonicalChunk(chunk); ref.setFactIndex(0);
                ref.setEvidenceDocument(evidence); ref.setSourcePage(1); ref.setEvidenceText("Synthetic supported fact.");
                when(refs.findByCanonicalChunk_IdInOrderByCanonicalChunk_IdAscFactIndexAscIdAsc(List.of(900L)))
                        .thenReturn(List.of(ref));
            } else {
                when(retrieval.searchCanonical(prepared, 3)).thenReturn(List.of());
                when(retrieval.searchEvidence(prepared, 3)).thenReturn(List.of(candidate(200, 100, DocumentRole.EVIDENCE, "Synthetic supported fact.")));
            }
            official.refuse = mode.equals("refusal");
            var rag = new RagService(retrieval, new AnswerabilityService(model), model, repos, refs, true, PublishedEvidenceFixture.gate());
            var result = rag.ask("synthetic fixture query");
            assertThat(result.getAnswer()).isNotBlank();
            if (official.refuse) {
                assertThat(result.getSources()).isEmpty(); assertThat(official.requests).hasSize(1);
            } else {
                assertThat(result.getAnswer()).contains("[S1]");
                assertThat(result.getSources()).hasSize(1);
                assertThat(result.getSources().getFirst().getDocumentId()).isEqualTo(100L);
                assertThat(result.getSources().getFirst().getSourceType()).isEqualTo("PERSONAL");
                assertThat(official.requests).hasSize(2);
            }
            official.requests.forEach(this::assertOfficial);
            assertThat(school.requests).isEmpty();
            assertThat(c.getBean(AiRemoteGuard.class).breaker("deepseekChat").getMetrics().getNumberOfFailedCalls()).isZero();
        });
    }

    static SearchResultResponse candidate(long chunk, long document, DocumentRole role, String text) {
        var r = new SearchResultResponse(); r.setChunkId(chunk); r.setDocumentId(document); r.setDocumentRole(role);
        r.setScore(0.99); r.setContent(text); r.setChunkIndex(0); return r;
    }
}
