package com.yu.transferrag.protection;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import java.util.List;

@ConfigurationProperties("app.rag.rate-limit")
public record RateLimitProperties(@DefaultValue("false") boolean enabled,
        @DefaultValue("10") double ipPerMinute, @DefaultValue("3") int ipCapacity,
        @DefaultValue("true") boolean globalEnabled, @DefaultValue("30") double globalPerMinute,
        @DefaultValue("5") int globalCapacity, @DefaultValue("") String hmacSecret,
        @DefaultValue List<String> trustedProxies) {
    public RateLimitProperties {
        if (!Double.isFinite(ipPerMinute) || !Double.isFinite(globalPerMinute) || ipPerMinute<=0 || globalPerMinute<=0
                || ipCapacity<1 || globalCapacity<1) throw new IllegalArgumentException("Invalid rate limit configuration");
        if (enabled && (hmacSecret==null || hmacSecret.length()<32)) throw new IllegalArgumentException("Rate limit HMAC secret must have at least 32 characters");
        trustedProxies=trustedProxies==null?List.of():List.copyOf(trustedProxies);
        trustedProxies.forEach(ClientIdentity::canonical);
    }
    @Override public String toString(){return "RateLimitProperties[enabled="+enabled+", hmacSecret=<redacted>]";}
}
