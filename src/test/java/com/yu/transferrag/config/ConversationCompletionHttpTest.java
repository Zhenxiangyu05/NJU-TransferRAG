package com.yu.transferrag.config;

import com.yu.transferrag.conversation.ConversationProperties;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import java.time.Duration;
import static org.assertj.core.api.Assertions.*;

/** Exact Spring SDK + isolated loopback providers; no official or school API call. */
class ConversationCompletionHttpTest {
    final ChatProviderIsolationTest fixture=new ChatProviderIsolationTest();
    @BeforeEach void start()throws Exception{fixture.start();}
    @AfterEach void stop(){fixture.close();}
    @Test void completionUsesSameOfficialProviderWithShorterRealDeadlineAndNoSdkRetry() {
        fixture.run(fixture.context("app.rag.conversation.enabled=true")
                .withBean(ConversationProperties.class,()->new ConversationProperties(true,Duration.ofMinutes(30),Duration.ofMinutes(5),Duration.ofMillis(200),4,500,4000)),c->{
            ChatModel completion=c.getBean("conversationCompletionChatModel",ChatModel.class);
            assertThat(((OpenAiChatOptions)completion.getDefaultOptions()).getTimeout()).isEqualTo(Duration.ofMillis(200));
            assertThat(((OpenAiChatOptions)c.getBean(ChatModel.class).getDefaultOptions()).getTimeout()).isEqualTo(Duration.ofSeconds(40));
            // Warm the shared SDK/JSON classes before measuring the HTTP deadline, not class loading/JIT.
            c.getBean(ChatModel.class).call(new Prompt("synthetic warmup"));fixture.official.requests.clear();
            fixture.official.delay=1000;long start=System.nanoTime();
            assertThatThrownBy(()->completion.call(new Prompt("synthetic"))).isInstanceOf(RuntimeException.class);
            assertThat((System.nanoTime()-start)/1_000_000).isBetween(100L,1500L);
            assertThat(fixture.official.requests).hasSize(1);fixture.assertOfficial(fixture.official.requests.getFirst());assertThat(fixture.school.requests).isEmpty();
        });
    }
    @Test void completionUsesExistingChatCircuitAndBulkhead() {
        fixture.run(fixture.context("app.rag.conversation.enabled=true")
                .withBean(ConversationProperties.class,()->new ConversationProperties(true,Duration.ofMinutes(30),Duration.ofMinutes(5),Duration.ofSeconds(8),4,500,4000)),c->{
            c.getBean(com.yu.transferrag.protection.AiRemoteGuard.class).breaker("deepseekChat").transitionToOpenState();
            assertThatThrownBy(()->c.getBean("conversationCompletionChatModel",ChatModel.class).call(new Prompt("synthetic")))
                    .isInstanceOf(com.yu.transferrag.protection.ProtectionException.class);assertThat(fixture.official.requests).isEmpty();
        });
    }
}
