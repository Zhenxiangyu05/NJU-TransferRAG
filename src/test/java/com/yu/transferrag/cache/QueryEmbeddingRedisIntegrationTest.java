package com.yu.transferrag.cache;

import io.lettuce.core.ClientOptions;
import io.lettuce.core.SocketOptions;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.*;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.Duration;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker=true)
class QueryEmbeddingRedisIntegrationTest {
    static final String PASSWORD=UUID.randomUUID().toString();
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379)
            .withCommand("redis-server","--requirepass",PASSWORD,"--maxmemory","64mb","--maxmemory-policy","allkeys-lru","--save","","--appendonly","no");
    LettuceConnectionFactory factory;RedisTemplate<String,byte[]> template;RedisEmbeddingCacheStore store;
    @BeforeEach void setup() {
        factory=connection(REDIS.getHost(),REDIS.getMappedPort(6379),PASSWORD);template=template(factory);store=new RedisEmbeddingCacheStore(template);
    }
    @AfterEach void close() {factory.destroy();}
    static LettuceConnectionFactory connection(String host,int port,String password) {
        var server=new RedisStandaloneConfiguration(host,port);server.setPassword(password);
        var timeout=Duration.ofMillis(150);
        var f=new LettuceConnectionFactory(server,LettuceClientConfiguration.builder().commandTimeout(timeout).shutdownTimeout(Duration.ofMillis(100))
                .clientOptions(ClientOptions.builder().autoReconnect(false).socketOptions(SocketOptions.builder().connectTimeout(timeout).build()).build()).build());
        f.afterPropertiesSet();f.start();return f;
    }
    static RedisTemplate<String,byte[]> template(LettuceConnectionFactory f) {
        var t=new RedisTemplate<String,byte[]>();t.setConnectionFactory(f);t.setKeySerializer(RedisSerializer.string());t.setValueSerializer(RedisSerializer.byteArray());t.afterPropertiesSet();return t;
    }
    QueryEmbeddingCache cache(EmbeddingModel model,String revision,Duration ttl) {
        return new QueryEmbeddingCache(model,store,new EmbeddingCacheProperties(true,ttl,0,1024,"v1",revision,"raw-v1",Duration.ofSeconds(5)),()->"isolated-bge-m3",new SimpleMeterRegistry());
    }
    static float[] vector() {float[] v=new float[1024];v[0]=1;v[25]=.25f;return v;}
    static EmbeddingModel model() {var m=mock(EmbeddingModel.class);when(m.embed(anyString())).thenReturn(vector());return m;}
    @Test void realRedisMissHitExactVectorAndNoPlaintextKey() {
        var m=model();var c=cache(m,"test-"+UUID.randomUUID(),Duration.ofHours(24));String q="isolated private question "+UUID.randomUUID();
        assertArrayEquals(c.embed(q),c.embed(q));verify(m,times(1)).embed(q);
        assertNotNull(store.get(c.key(q)));assertFalse(c.key(q).contains(q));assertEquals(1,c.stats().hits());
        Long ttl=template.getExpire(c.key(q));assertNotNull(ttl);assertTrue(ttl>86390 && ttl<=86400);
    }
    @Test void actualTtlExpiryCausesNewProviderCall() throws Exception {
        var m=model();var c=cache(m,"ttl-"+UUID.randomUUID(),Duration.ofMillis(200));c.embed("q");Thread.sleep(350);assertNull(store.get(c.key("q")));c.embed("q");verify(m,times(2)).embed("q");
    }
    @Test void restartOfCacheObjectUsesPersistedVectorAndVersionChangeMisses() {
        var storeRevision="restart-"+UUID.randomUUID();var first=model();cache(first,storeRevision,Duration.ofHours(1)).embed("q");
        var second=model();var fresh=cache(second,storeRevision,Duration.ofHours(1));assertArrayEquals(vector(),fresh.embed("q"));verify(second,never()).embed(anyString());
        cache(second,storeRevision+"-new",Duration.ofHours(1)).embed("q");verify(second,times(1)).embed("q");
    }
    @Test void corruptedRedisEntryIsRejectedWithoutFailingRequest() {
        var m=model();var c=cache(m,"corrupt-"+UUID.randomUUID(),Duration.ofHours(1));store.put(c.key("q"),new byte[10],Duration.ofMinutes(1));
        assertArrayEquals(vector(),c.embed("q"));assertTrue(c.stats().errors()>=1);verify(m).embed("q");
        assertArrayEquals(vector(),c.embed("q"));assertEquals(1,c.stats().hits());verify(m,times(1)).embed("q");
    }
    @Test void closedPortFailsOpenWithBoundedTimeoutAndBackoff() throws Exception {
        int closed;
        try(var socket=new java.net.ServerSocket(0,1,java.net.InetAddress.getLoopbackAddress())) {closed=socket.getLocalPort();}
        var down=connection("127.0.0.1",closed,PASSWORD);
        try {
            var m=model();var c=new QueryEmbeddingCache(m,new RedisEmbeddingCacheStore(template(down)),
                    new EmbeddingCacheProperties(true,Duration.ofHours(1),0,1024,"v1","offline","raw-v1",Duration.ofSeconds(5)),()->"isolated",new SimpleMeterRegistry());
            long start=System.nanoTime();assertArrayEquals(vector(),c.embed("q"));double elapsed=(System.nanoTime()-start)/1e6;
            assertTrue(elapsed<2000,"Redis fallback took too long");assertArrayEquals(vector(),c.embed("q2"));assertEquals(1,c.stats().errors());verify(m,times(2)).embed(anyString());
            System.out.printf("REDIS_OFFLINE_FALLBACK elapsed_ms=%.3f provider_calls=%d%n",elapsed,c.stats().embeddingCalls());
        } finally {down.destroy();}
    }
    @Test void unauthenticatedAccessIsRejected() {
        var noAuth=connection(REDIS.getHost(),REDIS.getMappedPort(6379),"");
        try {assertThrows(RuntimeException.class,()->template(noAuth).opsForValue().get("no-auth-test"));}finally{noAuth.destroy();}
    }
    @Test void actualHttpEmbeddingApiCalledOnceForMissThenHit() throws Exception {
        var calls=new java.util.concurrent.atomic.AtomicInteger();
        var inputs=new java.util.concurrent.CopyOnWriteArrayList<String>();
        var http=com.sun.net.httpserver.HttpServer.create(new java.net.InetSocketAddress("127.0.0.1",0),0);
        StringBuilder values=new StringBuilder("1");for(int i=1;i<1024;i++)values.append(",0");
        byte[] body=("{\"object\":\"list\",\"data\":[{\"object\":\"embedding\",\"index\":0,\"embedding\":["+values+"]}],\"model\":\"bge-m3\",\"usage\":{\"prompt_tokens\":1,\"total_tokens\":1}}").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        http.createContext("/v1/embeddings",exchange->{
            calls.incrementAndGet();inputs.add(new String(exchange.getRequestBody().readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
            exchange.getResponseHeaders().add("Content-Type","application/json");exchange.sendResponseHeaders(200,body.length);exchange.getResponseBody().write(body);exchange.close();
        });http.start();
        try {
            var model=org.springframework.ai.openai.OpenAiEmbeddingModel.builder()
                    .options(org.springframework.ai.openai.OpenAiEmbeddingOptions.builder().model("bge-m3").dimensions(1024)
                            .baseUrl("http://127.0.0.1:"+http.getAddress().getPort()+"/v1").apiKey("isolated-test-only").build()).build();
            var c=cache(model,"http-"+UUID.randomUUID(),Duration.ofHours(1));
            assertArrayEquals(c.embed("exact input "),c.embed("exact input "));assertEquals(1,calls.get());assertTrue(inputs.getFirst().contains("exact input "));
            c.embed("exact input");assertEquals(2,calls.get());
            System.out.println("QUERY_EMBEDDING_HTTP_PROOF same_input_requests=2 actual_http_calls=1 different_input_additional_http_calls=1");
        }finally{http.stop(0);}
    }
    @Test void redisRestartWithNoPersistenceLosesOnlyDisposableCache() throws Exception {
        // Keep restart/port-remapping isolated from every other test and user container.
        try(var isolated=new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379)
                .withCommand("redis-server","--requirepass",PASSWORD,"--save","","--appendonly","no")) {
            isolated.start();
            String revision="redis-restart-"+UUID.randomUUID();var m=model();
            var original=connection(isolated.getHost(),isolated.getMappedPort(6379),PASSWORD);
            String key;
            try {
                var originalStore=new RedisEmbeddingCacheStore(template(original));
                var c=new QueryEmbeddingCache(m,originalStore,
                        new EmbeddingCacheProperties(true,Duration.ofHours(1),0,1024,"v1",revision,"raw-v1",Duration.ofSeconds(5)),()->"isolated-bge-m3",new SimpleMeterRegistry());
                c.embed("q");key=c.key("q");assertNotNull(originalStore.get(key));
            }finally{original.destroy();}
            isolated.getDockerClient().restartContainerCmd(isolated.getContainerId()).exec();
            // Docker Desktop may remap ports on restart; do not use cached container inspection.
            var bindings=isolated.getDockerClient().inspectContainerCmd(isolated.getContainerId()).exec()
                    .getNetworkSettings().getPorts().getBindings().get(com.github.dockerjava.api.model.ExposedPort.tcp(6379));
            int restartedPort=Integer.parseInt(bindings[0].getHostPortSpec());
            var fresh=connection(isolated.getHost(),restartedPort,PASSWORD);
            try {
                org.awaitility.Awaitility.await().atMost(Duration.ofSeconds(15)).ignoreExceptions().until(()->{
                    try(var conn=fresh.getConnection()){return "PONG".equals(conn.ping());}
                });
                var freshStore=new RedisEmbeddingCacheStore(template(fresh));assertNull(freshStore.get(key));
            var reopened=new QueryEmbeddingCache(m,new RedisEmbeddingCacheStore(template(fresh)),
                    new EmbeddingCacheProperties(true,Duration.ofHours(1),0,1024,"v1",revision,"raw-v1",Duration.ofSeconds(5)),()->"isolated-bge-m3",new SimpleMeterRegistry());
            assertArrayEquals(vector(),reopened.embed("q"));verify(m,times(2)).embed("q");assertEquals(1,reopened.stats().misses());
                assertNotNull(freshStore.get(key));
            }finally{fresh.destroy();}
        }
    }
}
