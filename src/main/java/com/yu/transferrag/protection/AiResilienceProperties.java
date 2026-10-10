package com.yu.transferrag.protection;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.time.Duration;

@ConfigurationProperties("app.ai.resilience")
public record AiResilienceProperties(@DefaultValue("false") boolean enabled,
        @DefaultValue("20") int windowSize,@DefaultValue("10") int minimumCalls,
        @DefaultValue("50") float failureRate,@DefaultValue("30s") Duration openWait,
        @DefaultValue("2") int halfOpenCalls,@DefaultValue("2") int chatConcurrency,
        @DefaultValue("3") int embeddingConcurrency,@DefaultValue("60s") Duration chatTimeout,
        @DefaultValue("60s") Duration embeddingTimeout,@DefaultValue("0") int extraRetries,
        @DefaultValue("200ms") Duration retryWait) {
    public AiResilienceProperties {
        if(windowSize<1||minimumCalls<1||minimumCalls>windowSize||failureRate<=0||failureRate>100||!Float.isFinite(failureRate)
                ||halfOpenCalls<1||chatConcurrency<1||embeddingConcurrency<1||extraRetries<0||extraRetries>1
                ||openWait==null||openWait.isNegative()||openWait.isZero()||retryWait==null||retryWait.isNegative()
                ||chatTimeout==null||embeddingTimeout==null||chatTimeout.isNegative()||chatTimeout.isZero()
                ||embeddingTimeout.isNegative()||embeddingTimeout.isZero())throw new IllegalArgumentException("Invalid AI protection configuration");
    }
}
