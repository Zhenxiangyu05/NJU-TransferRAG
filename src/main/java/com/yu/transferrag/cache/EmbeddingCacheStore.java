package com.yu.transferrag.cache;

import java.time.Duration;

/** Only disposable query-vector bytes; never documents, permissions, or answers. */
public interface EmbeddingCacheStore {
    byte[] get(String key);
    void put(String key, byte[] value, Duration ttl);
}
