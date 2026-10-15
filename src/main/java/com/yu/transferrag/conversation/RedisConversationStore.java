package com.yu.transferrag.conversation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

/** Disposable, authenticated Redis state. Keys hash the capability token; there is no public history read API. */
public final class RedisConversationStore implements ConversationStore {
    private static final int MAX_STATE_BYTES = 65536;
    private static final DefaultRedisScript<String> CLAIM = new DefaultRedisScript<>("""
        if redis.call('EXISTS',KEYS[1]) == 0 then return 'EXPIRED' end
        if not redis.call('SET',KEYS[2],ARGV[1],'NX','PX',ARGV[2]) then return 'BUSY' end
        return redis.call('GET',KEYS[1])
        """, String.class);
    private static final DefaultRedisScript<Long> SAVE = new DefaultRedisScript<>("""
        if redis.call('GET',KEYS[2]) ~= ARGV[1] or redis.call('EXISTS',KEYS[1]) == 0 then return 0 end
        redis.call('SET',KEYS[1],ARGV[2],'PX',ARGV[3]); return 1
        """, Long.class);
    private static final DefaultRedisScript<Long> RELEASE = new DefaultRedisScript<>("""
        if redis.call('GET',KEYS[1]) == ARGV[1] then return redis.call('DEL',KEYS[1]) end
        return 0
        """, Long.class);
    private static final DefaultRedisScript<Long> DELETE = new DefaultRedisScript<>("""
        if redis.call('GET',KEYS[2]) ~= ARGV[1] then return 0 end
        redis.call('DEL',KEYS[1],KEYS[2]); return 1
        """, Long.class);
    private final StringRedisTemplate redis;
    private final ConversationProperties p;
    private final ObjectMapper json = new ObjectMapper();
    private final SecureRandom random = new SecureRandom();
    public RedisConversationStore(StringRedisTemplate redis, ConversationProperties p) { this.redis = redis; this.p = p; }
    public static String hash(String text) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8))); }
        catch (java.security.NoSuchAlgorithmException impossible) { throw new IllegalStateException(); }
    }
    static String key(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw ConversationException.expired();
        return "nju:conversation:v1:{" + hash(token) + "}";
    }
    @Override public Session create() {
        try {
            byte[] bytes = new byte[32]; random.nextBytes(bytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
            State state = State.empty();
            if (!Boolean.TRUE.equals(redis.opsForValue().setIfAbsent(key(token), json.writeValueAsString(state), p.ttl())))
                throw ConversationException.unavailable();
            return new Session(token, state.nextRequestId());
        } catch (ConversationException e) { throw e; }
        catch (Exception e) { throw ConversationException.unavailable(); }
    }
    @Override public Claim acquire(String token) {
        String k = key(token), owner = UUID.randomUUID().toString();
        try {
            String raw = redis.execute(CLAIM, List.of(k, k + ":lock"), owner, Long.toString(p.processingLease().toMillis()));
            if ("EXPIRED".equals(raw)) throw ConversationException.expired();
            if ("BUSY".equals(raw)) throw ConversationException.busy();
            if (raw == null || raw.getBytes(StandardCharsets.UTF_8).length > MAX_STATE_BYTES) throw ConversationException.unavailable();
            try { return new Claim(k, owner, json.readValue(raw, State.class)); }
            catch (Exception invalid) { redis.execute(RELEASE, List.of(k + ":lock"), owner); throw ConversationException.unavailable(); }
        } catch (ConversationException e) { throw e; }
        catch (Exception e) { throw ConversationException.unavailable(); }
    }
    @Override public void save(Claim c, State state) {
        try {
            String raw = json.writeValueAsString(state);
            if (raw.getBytes(StandardCharsets.UTF_8).length > MAX_STATE_BYTES) throw ConversationException.unavailable();
            if (!Long.valueOf(1).equals(redis.execute(SAVE, List.of(c.key(), c.key() + ":lock"), c.owner(), raw, Long.toString(p.ttl().toMillis()))))
                throw new ConversationException(409, "CONVERSATION_LEASE_LOST", "会话处理权已失效，本次结果未写入，请新建对话后重试完整问题。");
        } catch (ConversationException e) { throw e; }
        catch (Exception e) { throw ConversationException.unavailable(); }
    }
    @Override public void release(Claim c) {
        try { redis.execute(RELEASE, List.of(c.key() + ":lock"), c.owner()); }
        catch (RuntimeException ignored) { /* Lease expires; never replace an existing model error with cleanup failure. */ }
    }
    @Override public void delete(Claim c) {
        try {
            if (!Long.valueOf(1).equals(redis.execute(DELETE, List.of(c.key(), c.key() + ":lock"), c.owner()))) throw ConversationException.busy();
        } catch (ConversationException e) { throw e; }
        catch (RuntimeException e) { throw ConversationException.unavailable(); }
    }
}
