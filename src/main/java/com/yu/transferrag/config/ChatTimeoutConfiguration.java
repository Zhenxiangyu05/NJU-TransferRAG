package com.yu.transferrag.config;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.openai.autoconfigure.OpenAiAutoConfigurationUtil;
import org.springframework.ai.model.openai.autoconfigure.OpenAiChatProperties;
import org.springframework.ai.model.openai.autoconfigure.OpenAiCommonProperties;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import reactor.core.publisher.Flux;

import java.time.Duration;

/** Workaround for Spring AI 2.0.1 Chat properties losing timeout in toOptions(). */
@Configuration(proxyBeanMethods = false)
@org.springframework.context.annotation.Import(ChatProviderConfiguration.class)
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai", matchIfMissing = true)
public class ChatTimeoutConfiguration {
    /** Spring AI 2.0.1 creates these two clients privately and its final model is not closeable. */
    @Bean
    static org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor chatClientLifecycle() {
        return new org.springframework.beans.factory.config.DestructionAwareBeanPostProcessor() {
            private final java.util.Map<Object, java.util.List<Runnable>> owned = new java.util.IdentityHashMap<>();

            @Override
            public synchronized Object postProcessAfterInitialization(Object bean, String name) {
                if (name.equals("openAiChatModel") && bean instanceof OpenAiChatModel) {
                    var clients = new java.util.ArrayList<Runnable>();
                    for (String fieldName : java.util.List.of("openAiClient", "openAiClientAsync")) {
                        var field = org.springframework.util.ReflectionUtils.findField(OpenAiChatModel.class, fieldName);
                        if (field == null) throw new IllegalStateException("Unsupported Chat SDK lifecycle layout");
                        org.springframework.util.ReflectionUtils.makeAccessible(field);
                        Object client = org.springframework.util.ReflectionUtils.getField(field, bean);
                        // SDK interfaces expose close() but do not implement AutoCloseable.
                        if (client instanceof com.openai.client.OpenAIClient sync) clients.add(sync::close);
                        else if (client instanceof com.openai.client.OpenAIClientAsync async) clients.add(async::close);
                        else throw new IllegalStateException("Unsupported Chat SDK client type");
                    }
                    owned.put(bean, java.util.List.copyOf(clients));
                }
                return bean;
            }

            @Override
            public synchronized boolean requiresDestruction(Object bean) { return owned.containsKey(bean); }

            @Override
            public void postProcessBeforeDestruction(Object bean, String name) {
                java.util.List<Runnable> clients;
                synchronized (this) { clients = owned.remove(bean); }
                if (clients == null) return;
                RuntimeException failure = null;
                for (Runnable client : clients) try { client.run(); }
                catch (Exception error) {
                    if (failure == null) failure = new IllegalStateException("Chat SDK shutdown failed", error);
                    else failure.addSuppressed(error);
                }
                if (failure != null) throw failure;
            }
        };
    }

    @Bean
    @Primary
    ChatModel timeoutConfiguredChatModel(OpenAiChatModel delegate, OpenAiCommonProperties common,
                                        OpenAiChatProperties chat, Environment environment) {
        Duration timeout = OpenAiAutoConfigurationUtil.resolveCommonProperties(common, chat).getTimeout();
        if (environment.getProperty("app.ai.resilience.enabled", Boolean.class, false)) {
            Duration ceiling = org.springframework.boot.convert.DurationStyle.detectAndParse(
                    environment.getProperty("app.ai.resilience.chat-timeout", "60s"));
            if (ceiling.isZero() || ceiling.isNegative()) throw new IllegalArgumentException("Chat protection timeout must be positive");
            // Protection may tighten but never extend the Hotfix's configured timeout.
            if (timeout != null && ceiling.compareTo(timeout) < 0) timeout = ceiling;
        }
        return new TimeoutConfiguredChatModel(delegate, timeout, ChatProviderConfiguration.official(environment));
    }

    /** Owns no clients or executors: the original auto-configured model retains its lifecycle. */
    static final class TimeoutConfiguredChatModel implements ChatModel {
        private final OpenAiChatModel delegate;
        private final Duration timeout;
        private final boolean official;

        TimeoutConfiguredChatModel(OpenAiChatModel delegate, Duration timeout) {
            this(delegate, timeout, false);
        }

        TimeoutConfiguredChatModel(OpenAiChatModel delegate, Duration timeout, boolean official) {
            if (timeout == null || timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("Chat timeout must be positive");
            }
            this.delegate = delegate;
            this.timeout = timeout;
            this.official = official;
        }

        @Override
        public ChatResponse call(Prompt prompt) {
            return delegate.call(configure(prompt));
        }

        @Override
        public Flux<ChatResponse> stream(Prompt prompt) {
            return delegate.stream(configure(prompt));
        }

        @Override
        public ChatOptions getDefaultOptions() {
            return configureOptions(delegate.getOptions());
        }

        private Prompt configure(Prompt prompt) {
            ChatOptions supplied = prompt.getOptions();
            // The stock OpenAI model also requires OpenAiChatOptions for non-null options.
            OpenAiChatOptions options = supplied == null ? delegate.getOptions() : (OpenAiChatOptions) supplied;
            // Configuration is authoritative, including when a separate options builder fills in 60s.
            // mutate() preserves every generation/tool option and Prompt metadata/messages.
            return prompt.mutate().chatOptions(configureOptions(options)).build();
        }

        private OpenAiChatOptions configureOptions(OpenAiChatOptions options) {
            var builder = options.mutate().timeout(timeout);
            if (official) {
                // Prompt-local options must never redirect credentials or restore another provider/model.
                if (options.getBaseUrl() != null || options.getApiKey() != null || options.getCredential() != null
                        || (options.getCustomHeaders() != null && !options.getCustomHeaders().isEmpty())) {
                    throw new IllegalArgumentException("Per-prompt Chat connection overrides are disabled");
                }
                var body = new java.util.LinkedHashMap<String, Object>();
                if (options.getExtraBody() != null) body.putAll(options.getExtraBody());
                body.put("thinking", java.util.Map.of("type", "disabled"));
                builder.model(delegate.getOptions().getModel()).maxRetries(0).extraBody(body);
            }
            return builder.build();
        }
    }
}
