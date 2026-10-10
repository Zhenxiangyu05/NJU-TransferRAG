package com.yu.transferrag.cache;

import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.Supplier;

/** Cache Aside. Provider errors are never converted to cache misses or swallowed. */
public final class QueryEmbeddingCache {
    private static final Logger LOG = LoggerFactory.getLogger(QueryEmbeddingCache.class);
    private final EmbeddingModel model;
    private final EmbeddingCacheStore store;
    private final EmbeddingCacheProperties config;
    private final Supplier<String> modelIdentity;
    private final MeterRegistry meters;
    private com.yu.transferrag.protection.AiRemoteGuard remoteGuard;
    @org.springframework.beans.factory.annotation.Autowired(required=false)
    public void setRemoteGuard(com.yu.transferrag.protection.AiRemoteGuard guard){this.remoteGuard=guard;}
    private final ConcurrentHashMap<String, CompletableFuture<float[]>> flights = new ConcurrentHashMap<>();
    private final Semaphore flightSlots = new Semaphore(128);
    private final AtomicLong retryRedisAfter = new AtomicLong(), nextWarning = new AtomicLong();
    private final LongAdder hits=new LongAdder(), misses=new LongAdder(), errors=new LongAdder(), calls=new LongAdder(), coalesced=new LongAdder(), providerNanos=new LongAdder();
    public QueryEmbeddingCache(EmbeddingModel model, EmbeddingCacheStore store, EmbeddingCacheProperties config,
                               Supplier<String> modelIdentity, MeterRegistry meters) {
        this.model=model;this.store=store;this.config=config;this.modelIdentity=modelIdentity;this.meters=meters;
        meters.gauge("nju.embedding.cache.hit.ratio",this,c -> c.stats().hitRate());
    }
    public String signature() {
        return EmbeddingVectorCodec.sha(config.version()+"\0"+config.modelRevision()+"\0"+config.inputVersion()+"\0"+config.dimensions()+"\0"+modelIdentity.get());
    }
    public String key(String query) { return "nju:embedding:"+config.version()+":"+signature()+":"+EmbeddingVectorCodec.sha(query); }
    public float[] embed(String query) {
        Objects.requireNonNull(query,"Query must not be null");
        if (!config.enabled()) return provider(query); // No Redis access or cache validation in disabled mode.
        String signature=signature(), inputHash=EmbeddingVectorCodec.sha(query);
        String key="nju:embedding:"+config.version()+":"+signature+":"+inputHash;
        if (redisAvailable()) {
            float[] vector=read(key,signature,inputHash);
            if(vector!=null) { hits.increment();event("hit");return vector; }
        }
        misses.increment();event("miss");
        CompletableFuture<float[]> existing=flights.get(key);
        if(existing!=null) return await(existing);
        if(!flightSlots.tryAcquire()) return load(query,key,signature,inputHash);
        CompletableFuture<float[]> promise=new CompletableFuture<>();
        existing=flights.putIfAbsent(key,promise);
        if(existing!=null) { flightSlots.release();return await(existing); }
        try {
            // Recheck after becoming leader: another flight may just have persisted its result.
            if(redisAvailable()) {
                float[] result=read(key,signature,inputHash);
                if(result!=null) { promise.complete(result.clone());return result; }
            }
            float[] result=load(query,key,signature,inputHash);promise.complete(result.clone());return result;
        } catch(RuntimeException|Error error) { promise.completeExceptionally(error);throw error; }
        finally { flights.remove(key,promise);flightSlots.release(); }
    }
    private float[] await(CompletableFuture<float[]> future) {
        coalesced.increment();event("coalesced");
        try { return future.get().clone(); }
        catch(InterruptedException error) { Thread.currentThread().interrupt();throw new IllegalStateException("Query embedding wait interrupted",error); }
        catch(ExecutionException error) {
            if(error.getCause() instanceof RuntimeException runtime) throw runtime;
            if(error.getCause() instanceof Error fatal) throw fatal;
            throw new IllegalStateException("Query embedding failed",error.getCause());
        }
    }
    private float[] load(String query,String key,String signature,String inputHash) {
        float[] result=provider(query);
        // Invalid upstream values are not cached; do not hide provider failures behind Redis handling.
        EmbeddingVectorCodec.validate(result,config.dimensions());
        byte[] bytes=EmbeddingVectorCodec.encode(result,config.dimensions(),signature,inputHash);
        if(redisAvailable()) {
            try { store.put(key,bytes,ttl()); }
            catch(RuntimeException error) { cacheError(error,true); }
        }
        return result.clone();
    }
    private float[] provider(String query) {
        if(remoteGuard!=null && remoteGuard.enabled())return remoteGuard.embed(model,query,m->invokeModel(m,query));
        return invokeModel(model,query);
    }
    private float[] invokeModel(EmbeddingModel selected,String query) {
        calls.increment();meters.counter("nju.embedding.provider.calls").increment();long start=System.nanoTime();
        try { return selected.embed(query); }
        finally {
            long elapsed=System.nanoTime()-start;providerNanos.add(elapsed);
            meters.timer("nju.embedding.provider.duration").record(elapsed,TimeUnit.NANOSECONDS);
        }
    }
    private boolean redisAvailable() { return store!=null && System.nanoTime()>=retryRedisAfter.get(); }
    private float[] read(String key,String signature,String inputHash) {
        byte[] bytes;
        try { bytes=store.get(key); }
        catch(RuntimeException error) { cacheError(error,true);return null; }
        if(bytes==null)return null;
        try { return EmbeddingVectorCodec.decode(bytes,config.dimensions(),signature,inputHash); }
        catch(IllegalArgumentException error) { cacheError(error,false);return null; }
    }
    private Duration ttl() {
        double multiplier=config.ttlJitter()==0?1:ThreadLocalRandom.current().nextDouble(1-config.ttlJitter(),1+config.ttlJitter());
        return Duration.ofMillis(Math.max(1,Math.round(config.ttl().toMillis()*multiplier)));
    }
    private void event(String outcome) {
        meters.counter("nju.embedding.cache", "result",outcome).increment();LOG.debug("Query embedding cache outcome={}",outcome);
    }
    private void cacheError(RuntimeException error,boolean connectionFailure) {
        errors.increment();event("error");long now=System.nanoTime();
        if(connectionFailure)retryRedisAfter.set(now+config.errorBackoff().toNanos());
        long next=nextWarning.get();
        if(now>=next && nextWarning.compareAndSet(next,now+TimeUnit.SECONDS.toNanos(30)))
            LOG.warn("Query embedding cache unavailable/invalid: exceptionType={}; using provider",error.getClass().getSimpleName());
    }
    public Stats stats() {
        long h=hits.sum(),m=misses.sum();return new Stats(h,m,errors.sum(),calls.sum(),coalesced.sum(),h+m==0?0:(double)h/(h+m),providerNanos.sum()/1_000_000d);
    }
    public record Stats(long hits,long misses,long errors,long embeddingCalls,long coalesced,double hitRate,double embeddingMillis) {}
}
