package com.yu.transferrag.cache;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.embedding.EmbeddingModel;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class QueryEmbeddingCacheTest {
    static EmbeddingCacheProperties settings(boolean enabled) { return settings(enabled,"v1","r1",Duration.ofHours(24),0); }
    static EmbeddingCacheProperties settings(boolean enabled,String version,String revision,Duration ttl,double jitter) {
        return new EmbeddingCacheProperties(enabled,ttl,jitter,3,version,revision,"exact-utf8-v1",Duration.ZERO);
    }
    static class MemoryStore implements EmbeddingCacheStore {
        final Map<String,byte[]> entries=new ConcurrentHashMap<>();Duration lastTtl;
        public byte[] get(String key) { return entries.get(key); }
        public void put(String key,byte[] value,Duration ttl) { entries.put(key,value);lastTtl=ttl; }
    }
    static QueryEmbeddingCache cache(EmbeddingModel model,EmbeddingCacheStore store,EmbeddingCacheProperties p) {
        return new QueryEmbeddingCache(model,store,p,() -> "bge-m3@isolated-provider",new SimpleMeterRegistry());
    }
    static EmbeddingModel model() {
        var m=mock(EmbeddingModel.class);when(m.embed(anyString())).thenReturn(new float[]{1,0.25f,-0.5f});return m;
    }
    @Test void firstMissSecondHitAndExactTextKey() {
        var m=model();var store=new MemoryStore();var c=cache(m,store,settings(true));
        assertArrayEquals(c.embed("校园问题"),c.embed("校园问题"));
        verify(m,times(1)).embed("校园问题");assertEquals(1,c.stats().hits());assertEquals(1,c.stats().misses());
        assertEquals(.5,c.stats().hitRate());assertFalse(c.key("校园问题").contains("校园问题"));
        assertTrue(c.key("校园问题").matches("nju:embedding:v1:[a-f0-9]{64}:[a-f0-9]{64}"));
    }
    @Test void contextualIndependentQuestionsCannotShareEmbeddingCacheEntries() {
        var m=model();var c=cache(m,new MemoryStore(),settings(true));
        String software="2025年转软件工程考试考什么？",computer="2026年转计算机专业考试考什么？";
        assertNotEquals(c.key(software),c.key(computer));c.embed(software);c.embed(computer);c.embed(software);
        verify(m,times(1)).embed(software);verify(m,times(1)).embed(computer);
    }
    @Test void whitespaceAndDifferentQueriesAreNotNormalized() {
        var m=model();var c=cache(m,new MemoryStore(),settings(true));
        c.embed("test");c.embed("test ");c.embed("other");verify(m,times(3)).embed(anyString());
    }
    @Test void actualModelIdentityAndExplicitVersionsInvalidate() {
        var m=model();var store=new MemoryStore();var identity=new AtomicReference<>("bge-m3-revision-1");
        var c=new QueryEmbeddingCache(m,store,settings(true),identity::get,new SimpleMeterRegistry());
        c.embed("q");identity.set("bge-m3-revision-2");c.embed("q");
        cache(m,store,settings(true,"v2","r1",Duration.ofHours(24),0)).embed("q");
        cache(m,store,settings(true,"v1","r2",Duration.ofHours(24),0)).embed("q");
        verify(m,times(4)).embed("q");assertEquals(4,store.entries.size());
    }
    @Test void disabledNeverTouchesRedis() {
        var m=model();var store=mock(EmbeddingCacheStore.class);var c=cache(m,store,settings(false));
        c.embed("q");c.embed("q");verifyNoInteractions(store);verify(m,times(2)).embed("q");
    }
    @Test void corruptCachedValueFallsBackAndIsReplaced() {
        var m=model();var store=new MemoryStore();var c=cache(m,store,settings(true));store.entries.put(c.key("q"),new byte[]{1,2});
        c.embed("q");c.embed("q");verify(m,times(1)).embed("q");assertTrue(c.stats().errors()>0);
    }
    @Test void cacheBindingAndDimensionMismatchAreRejected() {
        var m=model();var store=new MemoryStore();var c=cache(m,store,settings(true));
        store.entries.put(c.key("q"),EmbeddingVectorCodec.encode(new float[]{1,2},2,c.signature(),EmbeddingVectorCodec.sha("q")));
        c.embed("q");verify(m).embed("q");
        byte[] wrong=EmbeddingVectorCodec.encode(new float[]{1,2,3},3,c.signature(),EmbeddingVectorCodec.sha("other"));
        store.entries.put(c.key("q"),wrong);c.embed("q");verify(m,times(2)).embed("q");
    }
    @Test void invalidProviderVectorsAreNeverCached() {
        for(float[] vector:new float[][]{new float[]{1},new float[]{0,0,0},new float[]{Float.NaN,1,2},new float[]{Float.POSITIVE_INFINITY,1,2}}) {
            var m=mock(EmbeddingModel.class);when(m.embed("q")).thenReturn(vector);var store=new MemoryStore();
            assertThrows(IllegalArgumentException.class,() -> cache(m,store,settings(true)).embed("q"));assertTrue(store.entries.isEmpty());
        }
    }
    @Test void nullVectorNotCached() {
        var m=mock(EmbeddingModel.class);var store=new MemoryStore();
        assertThrows(IllegalArgumentException.class,()->cache(m,store,settings(true)).embed("q"));assertTrue(store.entries.isEmpty());
    }
    @Test void providerErrorIsPropagatedAndNotNegativeCached() {
        var m=mock(EmbeddingModel.class);var failure=new IllegalStateException("test upstream failure");when(m.embed("q")).thenThrow(failure);
        var store=new MemoryStore();var c=cache(m,store,settings(true));
        assertSame(failure,assertThrows(IllegalStateException.class,()->c.embed("q")));
        assertSame(failure,assertThrows(IllegalStateException.class,()->c.embed("q")));assertTrue(store.entries.isEmpty());verify(m,times(2)).embed("q");
    }
    @Test void readFailureAndWriteFailureDoNotBreakProvider() {
        var m=model();var store=mock(EmbeddingCacheStore.class);when(store.get(anyString())).thenThrow(new IllegalStateException("isolated failure"));
        var c=cache(m,store,new EmbeddingCacheProperties(true,Duration.ofHours(24),0,3,"v1","r1","raw-v1",Duration.ofSeconds(5)));
        assertEquals(3,c.embed("q").length);assertEquals(3,c.embed("different").length);
        verify(store,times(1)).get(anyString());verify(store,never()).put(anyString(),any(),any());assertEquals(1,c.stats().errors());
        var writes=mock(EmbeddingCacheStore.class);doThrow(new IllegalStateException("isolated failure")).when(writes).put(anyString(),any(),any());
        assertEquals(3,cache(m,writes,settings(true)).embed("q").length);
    }
    @Test void jitterIsBoundedAndResultMutationDoesNotChangeCache() {
        var m=model();var store=new MemoryStore();var c=cache(m,store,settings(true,"v1","r1",Duration.ofHours(24),.1));
        float[] first=c.embed("q");first[0]=99;assertEquals(1,c.embed("q")[0]);
        assertTrue(store.lastTtl.toMillis()>=Duration.ofHours(24).toMillis()*.9);
        assertTrue(store.lastTtl.toMillis()<=Duration.ofHours(24).toMillis()*1.1);
    }
    @Test void concurrentSameQuerySingleFlightAndBoundedState() throws Exception {
        var m=model();var entered=new CountDownLatch(1);var release=new CountDownLatch(1);
        when(m.embed("q")).thenAnswer(call->{entered.countDown();assertTrue(release.await(5,TimeUnit.SECONDS));return new float[]{1,2,3};});
        var c=cache(m,new MemoryStore(),settings(true));
        try(var pool=Executors.newFixedThreadPool(12)) {
            List<Future<float[]>> results=new ArrayList<>();for(int i=0;i<12;i++)results.add(pool.submit(()->c.embed("q")));
            assertTrue(entered.await(5,TimeUnit.SECONDS));Thread.sleep(100);release.countDown();
            for(var result:results)assertArrayEquals(new float[]{1,2,3},result.get(5,TimeUnit.SECONDS));
        }
        verify(m,times(1)).embed("q");assertTrue(c.stats().coalesced()>0);
    }
    @Test void codecKeepsFloatBitsAndDetectsCorruption() {
        float[] v={Float.MIN_VALUE,Float.MAX_VALUE,-.0012345678f};String sig=EmbeddingVectorCodec.sha("model"),input=EmbeddingVectorCodec.sha("q");
        byte[] b=EmbeddingVectorCodec.encode(v,3,sig,input);assertArrayEquals(v,EmbeddingVectorCodec.decode(b,3,sig,input));b[80]^=1;
        assertThrows(IllegalArgumentException.class,()->EmbeddingVectorCodec.decode(b,3,sig,input));
    }
    @Test void onlySingleStringQueriesAreCachedNotDocumentBatches() {
        var m=model();var c=cache(m,new MemoryStore(),settings(true));var wrapper=new QueryOnlyEmbeddingModel(m,c);
        var doc=new org.springframework.ai.document.Document("test document");var opts=org.springframework.ai.embedding.EmbeddingOptions.builder().build();
        var batching=new org.springframework.ai.embedding.TokenCountBatchingStrategy();
        wrapper.embed("q");wrapper.embed("q");wrapper.embed(doc);wrapper.embed(List.of(doc),opts,batching);wrapper.embed(List.of("document text"));wrapper.dimensions();
        verify(m,times(1)).embed("q");verify(m).embed(doc);verify(m).embed(List.of(doc),opts,batching);verify(m).embed(List.of("document text"));verify(m).dimensions();
        assertEquals(1,c.stats().embeddingCalls());
    }
}
