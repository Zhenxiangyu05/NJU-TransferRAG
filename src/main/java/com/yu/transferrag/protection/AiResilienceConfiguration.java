package com.yu.transferrag.protection;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.*;
import org.springframework.core.env.Environment;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.*;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import reactor.core.publisher.Flux;

@Configuration(proxyBeanMethods=false)
@EnableConfigurationProperties(AiResilienceProperties.class)
public class AiResilienceConfiguration {
    @Bean(destroyMethod="close") AiRemoteGuard aiRemoteGuard(AiResilienceProperties p,ObjectProvider<MeterRegistry> meters,
            ObjectProvider<org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties> common,
            ObjectProvider<org.springframework.ai.model.openai.autoconfigure.OpenAiEmbeddingProperties> embedding){
        return new AiRemoteGuard(p,meters.getIfUnique(SimpleMeterRegistry::new),()->{
            var c=common.getIfAvailable();var e=embedding.getIfAvailable();
            return c!=null&&e!=null?org.springframework.ai.model.openai.autoconfigure.OpenAiAutoConfigurationUtil.resolveCommonProperties(c,e):null;
        });
    }
    @Bean static BeanPostProcessor protectedChatModel(Environment env,ObjectProvider<AiRemoteGuard> guards) {
        return new BeanPostProcessor(){@Override public Object postProcessAfterInitialization(Object bean,String name){
            if(!env.getProperty("app.ai.resilience.enabled",Boolean.class,false))return bean;
            if(bean instanceof OpenAiChatProperties properties){
                properties.setMaxRetries(0);
                properties.getOptions().setMaxRetries(0);
            }
            // Keep the concrete auto-configured client and its lifecycle intact. Decorate
            // only the primary timeout facade, after its authoritative Prompt options.
            if(name.equals("timeoutConfiguredChatModel") && bean instanceof ChatModel model)return new ChatModel(){
                @Override public ChatResponse call(Prompt prompt){return guards.getObject().call("deepseekChat",()->model.call(prompt));}
                @Override public ChatOptions getDefaultOptions(){return model.getDefaultOptions();}
                @Override public Flux<ChatResponse> stream(Prompt prompt){return Flux.error(new UnsupportedOperationException("Streaming is not enabled for protected RAG"));}
            };
            return bean;
        }};
    }
}
