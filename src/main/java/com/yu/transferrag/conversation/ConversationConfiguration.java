package com.yu.transferrag.conversation;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.*;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ConversationProperties.class)
public class ConversationConfiguration {
    @Bean
    @ConditionalOnProperty(name = "app.rag.conversation.enabled", havingValue = "true")
    ConversationStore conversationStore(@Qualifier("queryCacheRedisConnectionFactory") LettuceConnectionFactory factory, ConversationProperties p) {
        return new RedisConversationStore(new StringRedisTemplate(factory), p);
    }
}
