package com.yu.transferrag.conversation;

import org.junit.jupiter.api.*;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Only an isolated loopback-bound Redis; Docker absence is explicitly SKIP, never a mock PASS. */
@Testcontainers(disabledWithoutDocker = true)
class RedisConversationIntegrationTest {
    static final String PASSWORD = UUID.randomUUID().toString();
    @Container static GenericContainer<?> redis = new GenericContainer<>("redis:7.4-alpine")
            .withExposedPorts(6379).withCommand("redis-server", "--requirepass", PASSWORD, "--save", "", "--appendonly", "no")
            .withCreateContainerCmdModifier(c -> c.getHostConfig().withPortBindings(new com.github.dockerjava.api.model.PortBinding(
                    com.github.dockerjava.api.model.Ports.Binding.bindIpAndPort("127.0.0.1",0),new com.github.dockerjava.api.model.ExposedPort(6379))));
    LettuceConnectionFactory factory;StringRedisTemplate template;RedisConversationStore store;
    @BeforeEach void setup() {
        var server=new RedisStandaloneConfiguration(redis.getHost(),redis.getMappedPort(6379));server.setPassword(PASSWORD);
        factory=new LettuceConnectionFactory(server);factory.afterPropertiesSet();factory.start();template=new StringRedisTemplate(factory);
        store=new RedisConversationStore(template,StandaloneQuestionRewriterTest.properties());
    }
    @AfterEach void close(){if(factory!=null)factory.destroy();}
    @Test void secureCapabilityIsNotTheRedisKeyAndUnrelatedTokenCannotRead() {
        var session=store.create();assertThat(session.token()).matches("[A-Za-z0-9_-]{43}");
        assertThat(RedisConversationStore.key(session.token())).doesNotContain(session.token());
        assertThatThrownBy(()->store.acquire("z".repeat(43))).isInstanceOf(ConversationException.class);
        var c=store.acquire(session.token());assertThat(c.state().nextRequestId()).isEqualTo(session.requestId());store.release(c);
    }
    @Test void concurrentClaimIsAtomicAndReleaseAllowsNextRequest() {
        var s=store.create();var a=store.acquire(s.token());
        assertThatThrownBy(()->store.acquire(s.token())).isInstanceOf(ConversationException.class);
        store.release(a);var b=store.acquire(s.token());assertThat(b.owner()).isNotEqualTo(a.owner());store.release(b);
    }
    @Test void expiredLeaseCannotOverwriteNewOwnerOrUnlockItsClaim() {
        var s=store.create();var a=store.acquire(s.token());template.expire(a.key()+":lock",Duration.ofMillis(1));
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(2)).until(()->!Boolean.TRUE.equals(template.hasKey(a.key()+":lock")));
        var b=store.acquire(s.token());store.save(b,new ConversationStore.State(List.of(new ConversationStore.Turn("new","new")),List.of(),null,null,b.state().nextRequestId()));
        assertThatThrownBy(()->store.save(a,a.state())).isInstanceOf(ConversationException.class);store.release(a);
        assertThatThrownBy(()->store.acquire(s.token())).isInstanceOf(ConversationException.class);store.release(b);
        var checked=store.acquire(s.token());assertThat(checked.state().turns()).hasSize(1);store.release(checked);
    }
    @Test void expiredOrEvictedConversationCannotBeResurrectedByAnOldRequest() {
        var s=store.create();var a=store.acquire(s.token());template.delete(a.key());
        assertThatThrownBy(()->store.save(a,a.state())).isInstanceOf(ConversationException.class);
        assertThatThrownBy(()->store.acquire(s.token())).isInstanceOf(ConversationException.class);store.release(a);
    }
    @Test void stateAndBoundedReplaySurviveANewStoreInstance() {
        var s=store.create();var a=store.acquire(s.token());
        store.save(a,new ConversationStore.State(List.of(new ConversationStore.Turn("current","independent")),List.of(new ConversationStore.Receipt(s.requestId(),"hash","COMPLETED")),s.requestId(),ConversationServiceTest.answer("current"),UUID.randomUUID().toString()));store.release(a);
        var restarted=new RedisConversationStore(template,StandaloneQuestionRewriterTest.properties());var b=restarted.acquire(s.token());
        assertThat(b.state().turns()).hasSize(1);assertThat(b.state().replay().getAnswer()).contains("[S1]");assertThat(template.getExpire(b.key())).isBetween(1700L,1800L);restarted.release(b);
    }
    @Test void resetDeletesOnlyAuthenticatedSession() {
        var one=store.create();var two=store.create();var a=store.acquire(one.token());store.delete(a);store.release(a);
        assertThatThrownBy(()->store.acquire(one.token())).isInstanceOf(ConversationException.class);
        var b=store.acquire(two.token());store.release(b);
    }
    @Test void actualRedisTtlExpiresTheSessionWithoutCreatingAnEmptyReplacement() {
        var s=store.create();String k=RedisConversationStore.key(s.token());template.expire(k,Duration.ofMillis(1));
        org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(2)).until(()->!Boolean.TRUE.equals(template.hasKey(k)));
        assertThatThrownBy(()->store.acquire(s.token())).isInstanceOf(ConversationException.class);assertThat(template.hasKey(k)).isFalse();
    }
    ConversationService service(com.yu.transferrag.service.RagService rag) {
        var providers=mock(org.springframework.beans.factory.ObjectProvider.class);
        when(providers.getIfAvailable()).thenReturn(store);
        var rewrite=mock(StandaloneQuestionRewriter.class);
        when(rewrite.resolve(anyString(),anyList())).thenAnswer(i -> new StandaloneQuestionRewriter.Resolution(i.getArgument(0),false,false));
        var ready=mock(com.yu.transferrag.service.EvidenceReadinessService.class);
        when(ready.readyDocumentIds(any())).thenReturn(Set.of(16L));
        return new ConversationService(providers,StandaloneQuestionRewriterTest.properties(),rewrite,rag,ready);
    }
    @Test void actualRedisDuplicateSubmissionReplaysWithoutRepeatingRag() {
        var rag=mock(com.yu.transferrag.service.RagService.class);
        when(rag.ask(anyString())).thenAnswer(i -> ConversationServiceTest.answer(i.getArgument(0)));
        var service=service(rag);var session=store.create();
        var first=service.ask(session.token(),session.requestId(),"synthetic complete query");
        var duplicate=service.ask(session.token(),session.requestId(),"synthetic complete query");
        assertThat(duplicate.getNextRequestId()).isEqualTo(first.getNextRequestId());
        verify(rag,times(1)).ask(anyString());
        var state=store.acquire(session.token());assertThat(state.state().turns()).hasSize(1);store.release(state);
    }
    @Test void actualRedisNewConversationDeletesOnlyPreviousContext() {
        var rag=mock(com.yu.transferrag.service.RagService.class);
        when(rag.ask(anyString())).thenAnswer(i -> ConversationServiceTest.answer(i.getArgument(0)));
        var service=service(rag);var old=store.create();var other=store.create();
        service.ask(old.token(),old.requestId(),"synthetic complete query");
        var fresh=service.newConversation(old.token());
        assertThatThrownBy(()->store.acquire(old.token())).isInstanceOf(ConversationException.class);
        var newState=store.acquire((String)fresh.get("conversationToken"));assertThat(newState.state().turns()).isEmpty();store.release(newState);
        var unchanged=store.acquire(other.token());assertThat(unchanged.state().nextRequestId()).isEqualTo(other.requestId());store.release(unchanged);
    }
    @Test void lateRequestAfterLeaseExpiryCannotOverwriteNewlyCompletedHistory() throws Exception {
        var entered=new java.util.concurrent.CountDownLatch(1);var finish=new java.util.concurrent.CountDownLatch(1);
        var rag=mock(com.yu.transferrag.service.RagService.class);
        when(rag.ask("slow synthetic query")).thenAnswer(i->{entered.countDown();assertThat(finish.await(5,java.util.concurrent.TimeUnit.SECONDS)).isTrue();return ConversationServiceTest.answer(i.getArgument(0));});
        when(rag.ask("new synthetic query")).thenReturn(ConversationServiceTest.answer("new synthetic query"));
        var service=service(rag);var session=store.create();String key=RedisConversationStore.key(session.token());
        try(var executor=java.util.concurrent.Executors.newSingleThreadExecutor()) {
            var old=executor.submit(()->service.ask(session.token(),session.requestId(),"slow synthetic query"));
            assertThat(entered.await(3,java.util.concurrent.TimeUnit.SECONDS)).isTrue();
            var reserved=new com.fasterxml.jackson.databind.ObjectMapper().readValue(template.opsForValue().get(key),ConversationStore.State.class);
            template.expire(key+":lock",Duration.ofMillis(1));
            org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(2)).until(()->!Boolean.TRUE.equals(template.hasKey(key+":lock")));
            service.ask(session.token(),reserved.nextRequestId(),"new synthetic query");
            finish.countDown();
            assertThatThrownBy(()->old.get(3,java.util.concurrent.TimeUnit.SECONDS)).hasCauseInstanceOf(ConversationException.class);
            var checked=store.acquire(session.token());
            assertThat(checked.state().turns()).hasSize(1).first().extracting(ConversationStore.Turn::question).isEqualTo("new synthetic query");store.release(checked);
            assertThatThrownBy(()->service.ask(session.token(),session.requestId(),"slow synthetic query")).isInstanceOf(ConversationException.class);
            verify(rag,times(1)).ask("slow synthetic query");
        } finally {finish.countDown();}
    }
}
