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
import reactor.core.publisher.Flux;

import java.time.Duration;

/** Workaround for Spring AI 2.0.1 Chat properties losing timeout in toOptions(). */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "spring.ai.model.chat", havingValue = "openai", matchIfMissing = true)
public class ChatTimeoutConfiguration {
    @Bean
    @Primary
    ChatModel timeoutConfiguredChatModel(OpenAiChatModel delegate, OpenAiCommonProperties common,
                                        OpenAiChatProperties chat) {
        Duration timeout = OpenAiAutoConfigurationUtil.resolveCommonProperties(common, chat).getTimeout();
        return new TimeoutConfiguredChatModel(delegate, timeout);
    }

    /** Owns no clients or executors: the original auto-configured model retains its lifecycle. */
    static final class TimeoutConfiguredChatModel implements ChatModel {
        private final OpenAiChatModel delegate;
        private final Duration timeout;

        TimeoutConfiguredChatModel(OpenAiChatModel delegate, Duration timeout) {
            if (timeout == null || timeout.isNegative() || timeout.isZero()) {
                throw new IllegalArgumentException("Chat timeout must be positive");
            }
            this.delegate = delegate;
            this.timeout = timeout;
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
            return delegate.getOptions().mutate().timeout(timeout).build();
        }

        private Prompt configure(Prompt prompt) {
            ChatOptions supplied = prompt.getOptions();
            // The stock OpenAI model also requires OpenAiChatOptions for non-null options.
            OpenAiChatOptions options = supplied == null ? delegate.getOptions() : (OpenAiChatOptions) supplied;
            // Configuration is authoritative, including when a separate options builder fills in 60s.
            // mutate() preserves every generation/tool option and Prompt metadata/messages.
            return prompt.mutate().chatOptions(options.mutate().timeout(timeout).build()).build();
        }
    }
}
