package com.yu.transferrag.cache;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("app.cache.embedding")
public record EmbeddingCacheProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("24h") Duration ttl,
        @DefaultValue("0.1") double ttlJitter,
        @DefaultValue("1024") int dimensions,
        @DefaultValue("v1") String version,
        @DefaultValue("provider-default-v1") String modelRevision,
        @DefaultValue("exact-utf8-v1") String inputVersion,
        @DefaultValue("5s") Duration errorBackoff) {
    public EmbeddingCacheProperties {
        if (ttl == null || ttl.toMillis() < 1 || ttl.toDays() > 7 || dimensions < 1 || dimensions > 65536
                || !Double.isFinite(ttlJitter) || ttlJitter < 0 || ttlJitter > 0.25
                || version == null || !version.matches("[A-Za-z0-9._-]{1,32}")
                || modelRevision == null || modelRevision.isBlank() || inputVersion == null || inputVersion.isBlank()
                || errorBackoff == null || errorBackoff.isNegative())
            throw new IllegalArgumentException("Invalid query embedding cache configuration");
    }
}
