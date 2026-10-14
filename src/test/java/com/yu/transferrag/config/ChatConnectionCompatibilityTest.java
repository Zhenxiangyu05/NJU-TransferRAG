package com.yu.transferrag.config;

import com.openai.client.OpenAIClient;
import com.openai.core.ClientOptions;
import com.yu.transferrag.protection.*;
import org.junit.jupiter.api.*;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.openai.autoconfigure.*;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.openai.*;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.*;
import org.springframework.core.io.support.ResourcePropertySource;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

/** Actual auto-configured clients, environment-variable precedence, two loopback providers; synthetic keys only. */
class ChatConnectionCompatibilityTest {
    ChatProviderIsolationTest.Stub school, alternate;
    static final String SCHOOL="synthetic-school-only", CHAT="synthetic-chat-only", OFFICIAL="synthetic-official-only";
    @BeforeEach void start() throws Exception {school=new ChatProviderIsolationTest.Stub();alternate=new ChatProviderIsolationTest.Stub();}
    @AfterEach void stop(){school.close();alternate.close();}
    Map<String,Object> legacy() {
        var env=new HashMap<String,Object>();
        env.put("AI_BASE_URL",school.url()+"/v1");env.put("AI_API_KEY",SCHOOL);
        env.put("AI_CHAT_MODEL","legacy-school-chat");env.put("AI_EMBEDDING_MODEL","bge-m3");
        env.put("AI_CHAT_PROVIDER","njusehub");return env;
    }
    ApplicationContextRunner context(Map<String,Object> env) {
        return new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(OpenAiChatAutoConfiguration.class,OpenAiEmbeddingAutoConfiguration.class))
                .withUserConfiguration(ChatTimeoutConfiguration.class,AiResilienceConfiguration.class)
                .withBean(ToolCallingManager.class,()->mock(ToolCallingManager.class))
                .withInitializer(c->{
                    c.getEnvironment().getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
                    try{c.getEnvironment().getPropertySources().addLast(new ResourcePropertySource("classpath:application.properties"));}
                    catch(Exception e){throw new IllegalStateException(e);}
                    c.getEnvironment().getPropertySources().addFirst(new SystemEnvironmentPropertySource("synthetic-env",env));
                }).withPropertyValues("app.ai.resilience.enabled=true","app.ai.resilience.embedding-timeout=200ms");
    }
    static ClientOptions options(Object model) throws Exception {
        var f=OpenAiChatModel.class.getDeclaredField("openAiClient");f.setAccessible(true);Object sdk=f.get(model);
        var o=sdk.getClass().getDeclaredField("clientOptions");o.setAccessible(true);return (ClientOptions)o.get(sdk);
    }
    void assertWire(Map<String,Object> env,ChatProviderIsolationTest.Stub chatTarget,String chatKey,boolean official) {
        context(env).run(c->{
            assertThat(c).hasNotFailed().hasSingleBean(OpenAiChatModel.class).hasSingleBean(OpenAiEmbeddingModel.class);
            var embedding=c.getBean(OpenAiEmbeddingModel.class);
            try{
                var chat=c.getBean(ChatModel.class);chat.call(new Prompt("synthetic"));
                assertThat(((OpenAiChatOptions)chat.getDefaultOptions()).getTimeout()).isEqualTo(Duration.ofSeconds(40));
                try{assertThat(options(c.getBean(OpenAiChatModel.class)).maxRetries()).isZero();}catch(Exception e){throw new AssertionError(e);}
                assertThat(chatTarget.requests).hasSize(1);
                var request=chatTarget.requests.getFirst();
                assertThat(request.path()).isEqualTo(official?"/chat/completions":"/v1/chat/completions");
                assertThat(request.auth()).isEqualTo("Bearer "+chatKey);
                assertThat(request.body().path("model").asText()).isEqualTo(official?"deepseek-flash":"legacy-school-chat");
                if(official)assertThat(request.body().path("thinking").path("type").asText()).isEqualTo("disabled");
                else assertThat(request.body().has("thinking")).isFalse();
                assertThat(embedding.embed("synthetic import")).hasSize(1024);
                assertThat(c.getBean(AiRemoteGuard.class).embed(embedding,"synthetic query")).hasSize(1024);
                var embedded=school.requests.stream().filter(r->r.path().endsWith("embeddings")).toList();
                assertThat(embedded).hasSize(2).allSatisfy(r->{
                    assertThat(r.auth()).isEqualTo("Bearer "+SCHOOL).doesNotContain(OFFICIAL).doesNotContain(CHAT);
                    assertThat(r.path()).isEqualTo("/v1/embeddings");
                    assertThat(r.body().path("model").asText()).isEqualTo("bge-m3");
                    assertThat(r.body().has("thinking")).isFalse();assertThat(r.body().has("dimensions")).isFalse();
                });
                if(chatTarget==school)assertThat(alternate.requests).isEmpty();
                else assertThat(school.requests).hasSize(2);
            }finally{ChatProviderIsolationTest.closeEmbedding(embedding);}
        });
    }
    void independentEmbedding(Map<String,Object> env) {env.put("AI_EMBEDDING_BASE_URL",school.url()+"/v1");env.put("AI_EMBEDDING_API_KEY",SCHOOL);}
    @Test void legacyAiAliasesRemainCompatible(){assertWire(legacy(),school,SCHOOL,false);}
    @Test void publicSpringEnvironmentOverridesLegacyAiValues() {
        var env=legacy();env.put("AI_BASE_URL",alternate.url()+"/v1");env.put("AI_API_KEY",CHAT);
        env.put("SPRING_AI_OPENAI_BASE_URL",school.url()+"/v1");env.put("SPRING_AI_OPENAI_API_KEY",SCHOOL);
        assertWire(env,school,SCHOOL,false);
    }
    @Test void onlyPublicSpringConnectionNeedsNoLegacyAiConnection() {
        var env=legacy();env.remove("AI_BASE_URL");env.remove("AI_API_KEY");
        env.put("SPRING_AI_OPENAI_BASE_URL",school.url()+"/v1");env.put("SPRING_AI_OPENAI_API_KEY",SCHOOL);
        assertWire(env,school,SCHOOL,false);
    }
    @Test void dedicatedSpringChatWinsCommonAndChatAliases() {
        var env=legacy();independentEmbedding(env);
        env.put("AI_CHAT_BASE_URL",school.url()+"/v1");env.put("AI_CHAT_API_KEY",SCHOOL);
        env.put("SPRING_AI_OPENAI_CHAT_BASE_URL",alternate.url()+"/v1");env.put("SPRING_AI_OPENAI_CHAT_API_KEY",CHAT);
        assertWire(env,alternate,CHAT,false);
    }
    @Test void dedicatedChatAliasesWinCommon() {
        var env=legacy();independentEmbedding(env);env.put("AI_CHAT_BASE_URL",alternate.url()+"/v1");env.put("AI_CHAT_API_KEY",CHAT);
        assertWire(env,alternate,CHAT,false);
    }
    @Test void onlyDedicatedChatAndIndependentSchoolEmbeddingWorkWithoutCommonCredentials() {
        var env=legacy();env.remove("AI_BASE_URL");env.remove("AI_API_KEY");independentEmbedding(env);
        env.put("SPRING_AI_OPENAI_CHAT_BASE_URL",alternate.url()+"/v1");env.put("SPRING_AI_OPENAI_CHAT_API_KEY",CHAT);
        assertWire(env,alternate,CHAT,false);
    }
    @Test void officialSelectionOverridesSchoolChatAndNeverSharesKeys() {
        var env=legacy();independentEmbedding(env);env.put("AI_CHAT_PROVIDER","deepseek-official");
        env.put("DEEPSEEK_API_KEY",OFFICIAL);env.put("APP_AI_CHAT_DEEPSEEK_BASE_URL",alternate.url());
        assertWire(env,alternate,OFFICIAL,true);
    }
    @Test void officialKeyMissingFailsWithoutSendingAnyRequestOrFallingBack() {
        var env=legacy();independentEmbedding(env);env.put("AI_CHAT_PROVIDER","deepseek-official");
        context(env).run(c->assertThat(c).hasFailed());
        assertThat(school.requests).isEmpty();assertThat(alternate.requests).isEmpty();
    }
    @Test void explicitEnvironmentOverridesPackagedTimeoutAndRetryDefaults() {
        var env=legacy();env.put("APP_AI_RESILIENCE_ENABLED","false");
        env.put("SPRING_AI_OPENAI_CHAT_TIMEOUT","3s");env.put("SPRING_AI_OPENAI_CHAT_MAX_RETRIES","2");
        context(env).run(c->{
            assertThat(c).hasNotFailed();
            try{
                assertThat(((OpenAiChatOptions)c.getBean(ChatModel.class).getDefaultOptions()).getTimeout()).isEqualTo(Duration.ofSeconds(3));
                assertThat(options(c.getBean(OpenAiChatModel.class)).maxRetries()).isEqualTo(2);
            }catch(Exception e){throw new AssertionError(e);}
            finally{ChatProviderIsolationTest.closeEmbedding(c.getBean(OpenAiEmbeddingModel.class));}
        });
        assertThat(school.requests).isEmpty();assertThat(alternate.requests).isEmpty();
    }
}
