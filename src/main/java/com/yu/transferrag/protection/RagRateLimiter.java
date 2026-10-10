package com.yu.transferrag.protection;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.util.List;

public final class RagRateLimiter {
    private final RateLimitProperties p;private final StringRedisTemplate redis;private final MeterRegistry meters;
    @SuppressWarnings("rawtypes") private static final DefaultRedisScript<List> SCRIPT=script();
    @SuppressWarnings("rawtypes") private static DefaultRedisScript<List> script(){var s=new DefaultRedisScript<List>();s.setLocation(new ClassPathResource("redis/rag-token-bucket.lua"));s.setResultType(List.class);return s;}
    public RagRateLimiter(RateLimitProperties p,StringRedisTemplate redis,MeterRegistry meters){this.p=p;this.redis=redis;this.meters=meters;}
    public void check(String identity) {
        if(!p.enabled())return;
        List<?> result;
        try {
            result=redis.execute(SCRIPT,List.of("nju:rate:{rag}:ip:"+identity,"nju:rate:{rag}:global"),
                    String.valueOf(p.ipCapacity()),String.valueOf(p.ipPerMinute()/60),String.valueOf(p.globalCapacity()),
                    String.valueOf(p.globalPerMinute()/60),p.globalEnabled()?"1":"0");
            if(result==null||result.size()!=4)throw new IllegalStateException("Invalid limiter result");
        }catch(RuntimeException failure){meters.counter("nju.rag.limit","result","unavailable").increment();throw new ProtectionException(503,"RATE_LIMITER_UNAVAILABLE","请求保护服务暂不可用，请稍后重试。",1);}
        boolean allowed=((Number)result.get(0)).longValue()==1;
        meters.counter("nju.rag.limit","result",allowed?"allowed":"rejected").increment();
        if(!allowed)throw new ProtectionException(429,"RATE_LIMITED","请求过于频繁，请稍后重试。",((Number)result.get(3)).longValue());
    }
}
