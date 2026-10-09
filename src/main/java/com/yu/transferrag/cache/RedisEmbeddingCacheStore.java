package com.yu.transferrag.cache;

import java.time.Duration;
import org.springframework.data.redis.core.RedisTemplate;

public final class RedisEmbeddingCacheStore implements EmbeddingCacheStore {
    private final RedisTemplate<String, byte[]> redis;
    public RedisEmbeddingCacheStore(RedisTemplate<String, byte[]> redis) { this.redis = redis; }
    public byte[] get(String key) { return redis.opsForValue().get(key); }
    public void put(String key, byte[] value, Duration ttl) { redis.opsForValue().set(key, value, ttl); }
}
