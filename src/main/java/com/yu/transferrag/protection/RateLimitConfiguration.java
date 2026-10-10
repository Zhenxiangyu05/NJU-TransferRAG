package com.yu.transferrag.protection;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.config.annotation.*;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.*;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(RateLimitProperties.class)
@ConditionalOnProperty(name="app.rag.rate-limit.enabled",havingValue="true")
public class RateLimitConfiguration {
    @Bean RagRateLimiter ragRateLimiter(RateLimitProperties p,@Qualifier("queryCacheRedisConnectionFactory") LettuceConnectionFactory factory,ObjectProvider<MeterRegistry> meters){
        return new RagRateLimiter(p,new StringRedisTemplate(factory),meters.getIfUnique(SimpleMeterRegistry::new));
    }
    @Bean WebMvcConfigurer ragLimitWebMvcConfigurer(RagRateLimiter limiter,RateLimitProperties p){
        return new WebMvcConfigurer(){@Override public void addInterceptors(InterceptorRegistry registry){
            registry.addInterceptor(new HandlerInterceptor(){@Override public boolean preHandle(HttpServletRequest request,HttpServletResponse response,Object handler){
                if("POST".equals(request.getMethod()))limiter.check(ClientIdentity.hash(request,p));return true;
            }}).addPathPatterns("/api/rag/ask");
        }};
    }
}
