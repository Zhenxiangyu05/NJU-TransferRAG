package com.yu.transferrag.protection;

import com.yu.transferrag.controller.*;
import com.yu.transferrag.service.RagService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.servlet.HandlerInterceptor;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Testcontainers(disabledWithoutDocker=true)
class RateLimiterIntegrationTest {
    static final String PASSWORD=UUID.randomUUID().toString();
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379)
            .withCommand("redis-server","--requirepass",PASSWORD,"--save","","--appendonly","no","--maxmemory","64mb","--maxmemory-policy","allkeys-lru")
            .withCreateContainerCmdModifier(cmd->cmd.getHostConfig().withPortBindings(new com.github.dockerjava.api.model.PortBinding(
                    com.github.dockerjava.api.model.Ports.Binding.bindIpAndPort("127.0.0.1",0),new com.github.dockerjava.api.model.ExposedPort(6379))));
    LettuceConnectionFactory factory;StringRedisTemplate redis;
    @BeforeEach void setup(){var server=new RedisStandaloneConfiguration(REDIS.getHost(),REDIS.getMappedPort(6379));server.setPassword(PASSWORD);factory=new LettuceConnectionFactory(server);factory.afterPropertiesSet();factory.start();redis=new StringRedisTemplate(factory);
        // Isolated container owned by this test class only.
        try(var c=factory.getConnection()){c.serverCommands().flushDb();}
    }
    @AfterEach void close(){factory.destroy();}
    RateLimitProperties properties(double rate,int ipCap,int globalCap){return new RateLimitProperties(true,rate,ipCap,true,rate,globalCap,"test-only-identity-secret-32-characters",List.of("127.0.0.1","::1"));}
    RagRateLimiter limiter(RateLimitProperties p){return new RagRateLimiter(p,redis,new SimpleMeterRegistry());}
    @Test void burstRetryAfterAndTtl(){var l=limiter(properties(10,3,5));for(int i=0;i<3;i++)l.check("a");var e=assertThrows(ProtectionException.class,()->l.check("a"));assertEquals(429,e.status());assertEquals("RATE_LIMITED",e.code());assertTrue(e.retryAfter()>=5&&e.retryAfter()<=6);assertTrue(redis.getExpire("nju:rate:{rag}:ip:a")>0);}
    @Test void refillsUsingRedisTime() throws Exception {var l=limiter(properties(600,1,10));l.check("a");assertThrows(ProtectionException.class,()->l.check("a"));Thread.sleep(150);assertDoesNotThrow(()->l.check("a"));}
    @Test void differentIdentitiesIndependent(){var l=limiter(properties(1,1,5));l.check("a");l.check("b");assertThrows(ProtectionException.class,()->l.check("a"));}
    @Test void globalRejectDoesNotDebitOtherBucket(){var l=limiter(properties(.001,3,1));l.check("a");assertThrows(ProtectionException.class,()->l.check("b"));assertEquals(3,Double.parseDouble(redis.<String,String>opsForHash().get("nju:rate:{rag}:ip:b","tokens")));}
    @Test void ipRejectDoesNotDebitGlobal(){var l=limiter(properties(.001,1,5));l.check("a");assertThrows(ProtectionException.class,()->l.check("a"));assertTrue(Double.parseDouble(redis.<String,String>opsForHash().get("nju:rate:{rag}:global","tokens"))>=4);}
    @Test void actualLuaConcurrentRequestsNeverOverspend() throws Exception {
        var l=limiter(properties(.001,3,100));var pool=Executors.newFixedThreadPool(12);try{
            List<Callable<Boolean>> calls=new ArrayList<>();for(int i=0;i<50;i++)calls.add(()->{try{l.check("same");return true;}catch(ProtectionException e){assertEquals(429,e.status());return false;}});
            long allowed=0;for(var f:pool.invokeAll(calls))if(f.get())allowed++;assertEquals(3,allowed);
        }finally{pool.shutdownNow();}
    }
    @Test void noscriptAutomaticallyReloads(){var l=limiter(properties(1,3,5));l.check("a");try(var c=factory.getConnection()){c.scriptingCommands().scriptFlush();}l.check("b");}
    @Test void disabledNeverUsesRedis(){var p=new RateLimitProperties(false,10,3,true,30,5,"",List.of());assertDoesNotThrow(()->new RagRateLimiter(p,null,new SimpleMeterRegistry()).check("a"));}
    @Test void unavailableFailsClosed(){StringRedisTemplate broken=mock(StringRedisTemplate.class);when(broken.execute(any(),anyList(),any(Object[].class))).thenThrow(new IllegalStateException("synthetic"));var e=assertThrows(ProtectionException.class,()->new RagRateLimiter(properties(10,3,5),broken,new SimpleMeterRegistry()).check("a"));assertEquals(503,e.status());assertEquals("RATE_LIMITER_UNAVAILABLE",e.code());}
    @Test void spoofedXffNeverControlsIdentity(){var p=properties(10,3,5);var r=new MockHttpServletRequest();r.setRemoteAddr("203.0.113.5");r.addHeader("X-Forwarded-For","192.0.2.1");r.addHeader("X-Real-IP","192.0.2.2");String a=ClientIdentity.hash(r,p);r.removeHeader("X-Forwarded-For");r.removeHeader("X-Real-IP");assertEquals(a,ClientIdentity.hash(r,p));assertFalse(a.contains("203.0.113"));}
    @Test void trustedPeerUsesOverwrittenRealIpNotXff(){var p=properties(10,3,5);var r=new MockHttpServletRequest();r.setRemoteAddr("127.0.0.1");r.addHeader("X-Real-IP","2001:db8::1");r.addHeader("X-Forwarded-For","forged");String first=ClientIdentity.hash(r,p);r.removeHeader("X-Real-IP");r.addHeader("X-Real-IP","2001:0db8:0:0:0:0:0:1");assertEquals(first,ClientIdentity.hash(r,p));}
    @Test void evictionResetsBucketDocumentedLimitation(){var l=limiter(properties(.001,1,100));l.check("a");assertThrows(ProtectionException.class,()->l.check("a"));redis.delete("nju:rate:{rag}:ip:a");assertDoesNotThrow(()->l.check("a"));}
    @Test void actualAllkeysLruMemoryPressureCanEvictLimitState() {
        try(var c=factory.getConnection()){c.serverCommands().setConfig("maxmemory","2mb");}
        try{
            var l=limiter(properties(.001,1,100));l.check("victim");String payload="x".repeat(4096);
            for(int i=0;i<3000;i++)redis.opsForValue().set("pressure:"+i,payload);
            assertFalse(Boolean.TRUE.equals(redis.hasKey("nju:rate:{rag}:ip:victim")));
            try(var c=factory.getConnection()){assertTrue(Long.parseLong(c.serverCommands().info("stats").getProperty("evicted_keys"))>0);}
            assertDoesNotThrow(()->l.check("victim"));
        }finally{try(var c=factory.getConnection()){c.serverCommands().setConfig("maxmemory","64mb");}}
    }
    @Test void rejectedRequestCannotReachRagOrAi() throws Exception {
        var p=properties(.001,1,5);var l=limiter(p);var service=mock(RagService.class);var controller=new RagController(service);
        HandlerInterceptor gate=new HandlerInterceptor(){@Override public boolean preHandle(jakarta.servlet.http.HttpServletRequest r,jakarta.servlet.http.HttpServletResponse response,Object h){l.check(ClientIdentity.hash(r,p));return true;}};
        var mvc=MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new GlobalExceptionHandler()).addInterceptors(gate).build();
        var r=new MockHttpServletRequest();r.setRemoteAddr("127.0.0.1");l.check(ClientIdentity.hash(r,p));
        mvc.perform(post("/api/rag/ask").contentType("application/json").content("{\"question\":\"test\"}"))
                .andExpect(status().isTooManyRequests()).andExpect(header().exists("Retry-After")).andExpect(jsonPath("$.code").value("RATE_LIMITED"));verifyNoInteractions(service);
    }
}
