package com.yu.transferrag.config;

import com.yu.transferrag.cache.*;
import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.lettuce.core.resource.DefaultClientResources;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.ObservationRegistry;
import io.qdrant.client.QdrantClient;
import org.springframework.ai.embedding.BatchingStrategy;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.vectorstore.observation.VectorStoreObservationConvention;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.ai.vectorstore.qdrant.autoconfigure.QdrantVectorStoreProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import java.time.Duration;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(EmbeddingCacheProperties.class)
public class QueryEmbeddingCacheConfiguration {
    @Bean
    QueryEmbeddingCache queryEmbeddingCache(EmbeddingModel original,EmbeddingCacheProperties properties,
            ObjectProvider<EmbeddingCacheStore> stores,ObjectProvider<MeterRegistry> meters,Environment env) {
        return new QueryEmbeddingCache(original,stores.getIfAvailable(),properties,() -> {
            String actualModel=env.getProperty("spring.ai.openai.embedding.model", "unknown");
            String actualDimensions="default";
            String endpoint=env.getProperty("spring.ai.openai.embedding.base-url",env.getProperty("spring.ai.openai.base-url","unknown"));
            String extraOptions="";
            if(original instanceof OpenAiEmbeddingModel openai) {
                actualModel=openai.getOptions().getModel();
                actualDimensions=String.valueOf(openai.getOptions().getDimensions());
                if(openai.getOptions().getBaseUrl()!=null)endpoint=openai.getOptions().getBaseUrl();
                try {
                    extraOptions=String.valueOf(openai.getOptions().getEncodingFormat())+"\0"
                            +new com.fasterxml.jackson.databind.ObjectMapper()
                            .enable(com.fasterxml.jackson.databind.SerializationFeature.ORDER_MAP_ENTRIES_BY_KEYS)
                            .writeValueAsString(openai.getOptions().getExtraBody());
                }catch(com.fasterxml.jackson.core.JsonProcessingException error) {
                    throw new IllegalArgumentException("Unsupported embedding cache model options");
                }
            }
            return original.getClass().getName()+"\0"+actualModel+"\0"+actualDimensions+"\0"+endpoint+"\0"+extraOptions;
        },meters.getIfUnique(SimpleMeterRegistry::new));
    }

    @Configuration(proxyBeanMethods=false)
    @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("${app.cache.embedding.enabled:false} or ${app.rag.rate-limit.enabled:false} or ${app.ai.resilience.enabled:false}")
    @EnableConfigurationProperties(QdrantVectorStoreProperties.class)
    static class Enabled {
        @Bean(destroyMethod="shutdown")
        @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("${app.cache.embedding.enabled:false} or ${app.rag.rate-limit.enabled:false}")
        DefaultClientResources queryCacheRedisResources() {
            return DefaultClientResources.builder().ioThreadPoolSize(2).computationThreadPoolSize(2).build();
        }
        @Bean
        @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("${app.cache.embedding.enabled:false} or ${app.rag.rate-limit.enabled:false}")
        LettuceConnectionFactory queryCacheRedisConnectionFactory(
                @Qualifier("queryCacheRedisResources") DefaultClientResources resources,Environment env) {
            var server=new RedisStandaloneConfiguration(env.getProperty("app.cache.embedding.redis.host","127.0.0.1"),
                    env.getProperty("app.cache.embedding.redis.port",Integer.class,16379));
            server.setDatabase(env.getProperty("app.cache.embedding.redis.database",Integer.class,0));
            String password=env.getProperty("app.cache.embedding.redis.password","");
            if(!password.isBlank()) server.setPassword(password);
            String username=env.getProperty("app.cache.embedding.redis.username","");
            if(!username.isBlank()) server.setUsername(username);
            Duration timeout=org.springframework.boot.convert.DurationStyle.detectAndParse(
                    env.getProperty("app.cache.embedding.redis.timeout","200ms"));
            if(timeout.compareTo(Duration.ofMillis(1))<0||timeout.compareTo(Duration.ofSeconds(1))>0)
                throw new IllegalArgumentException("Query cache Redis timeout must be between 1ms and 1s");
            var options=ClientOptions.builder().autoReconnect(true).requestQueueSize(128)
                    .disconnectedBehavior(ClientOptions.DisconnectedBehavior.REJECT_COMMANDS)
                    .socketOptions(SocketOptions.builder().connectTimeout(timeout).build()).build();
            return new LettuceConnectionFactory(server,LettuceClientConfiguration.builder().clientResources(resources)
                    .clientOptions(options).commandTimeout(timeout).shutdownTimeout(Duration.ofMillis(100)).build());
        }
        @Bean
        @ConditionalOnProperty(name="app.cache.embedding.enabled",havingValue="true")
        EmbeddingCacheStore queryEmbeddingCacheStore(@Qualifier("queryCacheRedisConnectionFactory") LettuceConnectionFactory factory) {
            RedisTemplate<String,byte[]> template=new RedisTemplate<>();template.setConnectionFactory(factory);
            template.setKeySerializer(RedisSerializer.string());template.setValueSerializer(RedisSerializer.byteArray());
            template.afterPropertiesSet();return new RedisEmbeddingCacheStore(template);
        }
        /** Same builder/settings as Spring AI 2.0.1 auto-config; only its query embedding delegate changes. */
        @Bean
        @org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("${app.cache.embedding.enabled:false} or ${app.ai.resilience.enabled:false}")
        QdrantVectorStore vectorStore(EmbeddingModel original,QueryEmbeddingCache cache,QdrantVectorStoreProperties properties,
                QdrantClient client,ObjectProvider<ObservationRegistry> observations,
                ObjectProvider<VectorStoreObservationConvention> convention,BatchingStrategy batches) {
            return QdrantVectorStore.builder(client,new QueryOnlyEmbeddingModel(original,cache))
                    .collectionName(properties.getCollectionName()).contentFieldName(properties.getContentFieldName())
                    .initializeSchema(properties.isInitializeSchema())
                    .observationRegistry(observations.getIfUnique(() -> ObservationRegistry.NOOP))
                    .customObservationConvention(convention.getIfAvailable()).batchingStrategy(batches).build();
        }
    }
}
