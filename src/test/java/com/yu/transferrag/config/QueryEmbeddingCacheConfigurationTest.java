package com.yu.transferrag.config;

import com.yu.transferrag.cache.*;
import io.qdrant.client.QdrantClient;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class QueryEmbeddingCacheConfigurationTest {
    @Test void limiterCanReuseClientWhileCacheStaysDisabled() {
        context.withUserConfiguration(com.yu.transferrag.protection.RateLimitConfiguration.class)
                .withPropertyValues("app.rag.rate-limit.enabled=true","app.rag.rate-limit.hmac-secret=synthetic-32-character-only-test-secret")
                .run(c->{assertThat(c).hasNotFailed();assertThat(c).hasSingleBean(LettuceConnectionFactory.class);
                    assertThat(c).doesNotHaveBean(EmbeddingCacheStore.class);
                    assertThat(c.getBean(QueryEmbeddingCache.class).embed("q")).containsExactly(1,2,3);
                    var failure=org.junit.jupiter.api.Assertions.assertThrows(com.yu.transferrag.protection.ProtectionException.class,
                            ()->c.getBean(com.yu.transferrag.protection.RagRateLimiter.class).check("isolated"));
                    assertThat(failure.code()).isEqualTo("RATE_LIMITER_UNAVAILABLE");});
    }
    final ApplicationContextRunner context=new ApplicationContextRunner()
            .withUserConfiguration(QueryEmbeddingCacheConfiguration.class,Fixture.class)
            .withConfiguration(AutoConfigurations.of(QdrantVectorStoreAutoConfiguration.class))
            .withPropertyValues("spring.ai.vectorstore.qdrant.initialize-schema=false",
                    "spring.ai.vectorstore.qdrant.collection-name=isolated_test",
                    "spring.ai.openai.embedding.model=isolated-model","app.cache.embedding.dimensions=3",
                    "app.cache.embedding.redis.host=127.0.0.1","app.cache.embedding.redis.port=1",
                    "app.cache.embedding.redis.timeout=50ms");
    @Configuration(proxyBeanMethods=false)
    static class Fixture {
        @Bean EmbeddingModel originalModel() {var m=mock(EmbeddingModel.class);when(m.dimensions()).thenReturn(3);when(m.embed(anyString())).thenReturn(new float[]{1,2,3});return m;}
        @Bean QdrantClient client() {return mock(QdrantClient.class);}
    }
    @Test void defaultOffUsesStockVectorStoreAndCreatesNoRedisConnection() {
        context.run(c->{
            assertThat(c).hasNotFailed().hasSingleBean(QdrantVectorStore.class).hasSingleBean(QueryEmbeddingCache.class);
            assertThat(c).doesNotHaveBean(LettuceConnectionFactory.class).doesNotHaveBean(EmbeddingCacheStore.class);
            assertThat(c.getBean(QueryEmbeddingCache.class).embed("q")).containsExactly(1,2,3);
        });
    }
    @Test void enabledStartsWithoutRedisAndDoesNotReplaceOriginalModel() {
        context.withPropertyValues("app.cache.embedding.enabled=true").run(c->{
            assertThat(c).hasNotFailed().hasSingleBean(QdrantVectorStore.class).hasSingleBean(EmbeddingModel.class)
                    .hasSingleBean(LettuceConnectionFactory.class).hasSingleBean(EmbeddingCacheStore.class);
            var cache=c.getBean(QueryEmbeddingCache.class);assertThat(cache.embed("q")).containsExactly(1,2,3);
            assertThat(cache.stats().errors()).isEqualTo(1);
            // The only global EmbeddingModel remains original, so FrozenEvidenceIndexer and import paths stay uncached.
            assertThat(c.getBean(EmbeddingModel.class)).isSameAs(c.getBean("originalModel"));
        });
    }
}
