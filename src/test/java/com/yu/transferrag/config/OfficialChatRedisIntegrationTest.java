package com.yu.transferrag.config;

import com.yu.transferrag.cache.QueryEmbeddingCache;
import com.yu.transferrag.protection.*;
import io.qdrant.client.QdrantClient;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;

import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker = true)
class OfficialChatRedisIntegrationTest {
    static final String PASSWORD = UUID.randomUUID().toString();
    @Container static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379).withCommand("redis-server", "--requirepass", PASSWORD, "--save", "", "--appendonly", "no")
            .withCreateContainerCmdModifier(cmd -> cmd.getHostConfig().withPortBindings(new com.github.dockerjava.api.model.PortBinding(
                    com.github.dockerjava.api.model.Ports.Binding.bindIpAndPort("127.0.0.1", 0),
                    new com.github.dockerjava.api.model.ExposedPort(6379))));
    final ChatProviderIsolationTest fixture = new ChatProviderIsolationTest();
    @BeforeEach void start() throws Exception { fixture.start(); }
    @AfterEach void stop() { fixture.close(); }
    @Configuration(proxyBeanMethods = false)
    static class VectorFixture { @Bean QdrantClient client() { return mock(QdrantClient.class); } }

    ApplicationContextRunner context(String provider, String version) {
        return fixture.context("app.ai.chat.provider=" + provider)
                .withConfiguration(AutoConfigurations.of(QdrantVectorStoreAutoConfiguration.class))
                .withUserConfiguration(QueryEmbeddingCacheConfiguration.class, VectorFixture.class, RateLimitConfiguration.class)
                .withPropertyValues("app.cache.embedding.enabled=true", "app.cache.embedding.version=" + version,
                        "app.cache.embedding.redis.host=" + REDIS.getHost(),
                        "app.cache.embedding.redis.port=" + REDIS.getMappedPort(6379),
                        "app.cache.embedding.redis.password=" + PASSWORD,
                        "app.rag.rate-limit.enabled=true", "app.rag.rate-limit.hmac-secret=synthetic-fixture-32-character-secret",
                        "spring.ai.vectorstore.qdrant.initialize-schema=false");
    }

    @Test void providerSwitchKeepsExistingRedisKeyAndSchoolVector() {
        String version = UUID.randomUUID().toString().replace("-", "");
        String[] key = new String[1];
        fixture.run(context("njusehub", version), c -> {
            var cache = c.getBean(QueryEmbeddingCache.class);
            key[0] = cache.key("synthetic query");
            assertThat(cache.embed("synthetic query")).hasSize(1024);
            assertThat(cache.stats().misses()).isEqualTo(1);
            assertThat(fixture.school.requests).hasSize(1);
        });
        fixture.run(context("deepseek-official", version), c -> {
            var cache = c.getBean(QueryEmbeddingCache.class);
            assertThat(cache.key("synthetic query")).isEqualTo(key[0]);
            c.getBean(AiRemoteGuard.class).breaker("queryEmbedding").transitionToOpenState();
            assertThat(cache.embed("synthetic query")).hasSize(1024);
            assertThat(cache.stats().hits()).isEqualTo(1);
            assertThat(cache.stats().embeddingCalls()).isZero();
            assertThat(fixture.school.requests).hasSize(1);
            c.getBean(ChatModel.class).call(new Prompt("synthetic"));
            fixture.assertOfficial(fixture.official.requests.getFirst());
            assertThat(c.getBean(RagRateLimiter.class)).isNotNull();
        });
    }
}
