package com.yu.transferrag.service;

import com.yu.transferrag.cache.*;
import com.yu.transferrag.dto.*;
import com.yu.transferrag.entity.*;
import com.yu.transferrag.repository.*;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.qdrant.client.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.*;
import java.time.Duration;
import java.util.*;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real Redis/Qdrant, isolated synthetic evidence and deterministic stub embedding/chat. No production config. */
@Testcontainers(disabledWithoutDocker=true)
class QueryEmbeddingRagIntegrationTest {
    static final String PASSWORD=UUID.randomUUID().toString();
    @Container static final GenericContainer<?> REDIS=new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379)
            .withCommand("redis-server","--requirepass",PASSWORD,"--maxmemory","64mb","--maxmemory-policy","allkeys-lru","--save","","--appendonly","no");
    @Container static final GenericContainer<?> QDRANT=new GenericContainer<>("qdrant/qdrant:v1.19.1").withExposedPorts(6333,6334);
    QdrantClient client;LettuceConnectionFactory redis;EmbeddingCacheStore store;EmbeddingModel model;
    QdrantVectorStore baseline,cached;QueryEmbeddingCache off,on;QueryRewriteService rewrite;
    DocumentRepository documents;EvidenceReadinessService readiness;DocumentReadinessView ready;
    com.yu.transferrag.entity.Document evidence;String collection;
    static float[] vector() {float[] v=new float[1024];v[0]=1;v[12]=.25f;return v;}
    QueryEmbeddingCache cache(boolean enabled) {
        return new QueryEmbeddingCache(model,store,new EmbeddingCacheProperties(enabled,Duration.ofHours(24),0,1024,"v1",collection,"raw-v1",Duration.ofSeconds(5)),()->"isolated-bge-m3",new SimpleMeterRegistry());
    }
    @BeforeEach void setup() throws Exception {
        var server=new RedisStandaloneConfiguration(REDIS.getHost(),REDIS.getMappedPort(6379));server.setPassword(PASSWORD);
        redis=new LettuceConnectionFactory(server);redis.afterPropertiesSet();redis.start();
        RedisTemplate<String,byte[]> template=new RedisTemplate<>();template.setConnectionFactory(redis);template.setKeySerializer(RedisSerializer.string());template.setValueSerializer(RedisSerializer.byteArray());template.afterPropertiesSet();store=new RedisEmbeddingCacheStore(template);
        client=new QdrantClient(QdrantGrpcClient.newBuilder(QDRANT.getHost(),QDRANT.getMappedPort(6334),false).build());
        model=mock(EmbeddingModel.class);when(model.dimensions()).thenReturn(1024);
        when(model.embed(anyString())).thenAnswer(call->{Thread.sleep(30);return vector();});
        when(model.embed(anyList(),any(),any())).thenAnswer(call->{List<?> docs=call.getArgument(0);return docs.stream().map(d->vector()).toList();});
        collection="isolated_embedding_cache_"+UUID.randomUUID().toString().replace("-","");off=cache(false);on=cache(true);
        baseline=QdrantVectorStore.builder(client,new QueryOnlyEmbeddingModel(model,off)).collectionName(collection).initializeSchema(true).build();baseline.afterPropertiesSet();
        cached=QdrantVectorStore.builder(client,new QueryOnlyEmbeddingModel(model,on)).collectionName(collection).initializeSchema(false).build();cached.afterPropertiesSet();
        baseline.add(List.of(point("EVIDENCE",1,11,"Synthetic fixture fact."),point("CANONICAL",2,22,"[F0] Synthetic fixture fact.")));
        rewrite=mock(QueryRewriteService.class);when(rewrite.rewriteWithContext(anyString())).thenAnswer(call->{String q=call.getArgument(0);return new QueryRewriteResult(q,q,List.of(),2026,2026,false);});
        documents=mock(DocumentRepository.class);ready=mock(DocumentReadinessView.class);
        when(ready.getDocumentId()).thenReturn(1L);when(ready.getDocumentRole()).thenReturn(DocumentRole.EVIDENCE);
        when(ready.getTaskId()).thenReturn(1L);when(ready.getTaskKind()).thenReturn(ImportTaskKind.NEW_EVIDENCE);when(ready.getStatus()).thenReturn(ImportTaskStatus.SUCCEEDED);
        when(documents.findReadinessByIds(anyCollection())).thenReturn(List.of(ready));readiness=new EvidenceReadinessService(documents);
        evidence=new com.yu.transferrag.entity.Document();evidence.setId(1L);evidence.setDocumentRole(DocumentRole.EVIDENCE);evidence.setTitle("Isolated evidence");evidence.setSourceType("PERSONAL");evidence.setYear(2026);evidence.setScope("DEPARTMENT");
        when(documents.findAllById(any())).thenReturn(List.of(evidence));
    }
    org.springframework.ai.document.Document point(String role,long doc,long chunk,String text) {
        return org.springframework.ai.document.Document.builder().id(EvidenceChunkPersistenceService.pointId(doc,chunk)).text(text)
                .metadata(Map.of("documentId",doc,"chunkId",chunk,"chunkIndex",0,"documentRole",role,"year",2026,"effectiveYear",2026,"sourceType",role.equals("EVIDENCE")?"PERSONAL":"CURATED","scope","DEPARTMENT")).build();
    }
    RetrievalService retrieval(boolean cacheOn) {
        var vectors=cacheOn?cached:baseline;var pre=new QdrantPrecomputedVectorSearch(vectors,model,collection);pre.setQueryEmbeddingCache(cacheOn?on:off);
        return new RetrievalService(vectors,rewrite,mock(ChunkRepository.class),pre,readiness);
    }
    @AfterEach void close() {if(client!=null)client.close();if(redis!=null)redis.destroy();}
    @ParameterizedTest @ValueSource(booleans={false,true})
    void identicalVectorsCandidatesAndMeasuredLocalLatency(boolean prepared) throws Exception {
        String q="isolated query "+prepared;var old=retrieval(false);var current=retrieval(true);List<Long> expected=null;
        long start=System.nanoTime();
        for(int i=0;i<3;i++) {
            List<SearchResultResponse> result;
            if(prepared) {
                var query=old.prepareCanonicalFirst(q);
                assertEquals(List.of(22L),old.searchCanonical(query,3).stream().map(SearchResultResponse::getChunkId).toList());
                result=old.searchEvidence(query,3);
            } else result=old.search(q,3);
            expected=result.stream().map(SearchResultResponse::getChunkId).toList();assertEquals(List.of(11L),expected);
        }
        double before=(System.nanoTime()-start)/1e6;start=System.nanoTime();
        for(int i=0;i<3;i++) {
            List<SearchResultResponse> result;
            if(prepared) {
                var query=current.prepareCanonicalFirst(q);assertEquals(List.of(22L),current.searchCanonical(query,3).stream().map(SearchResultResponse::getChunkId).toList());
                result=current.searchEvidence(query,3); // Same query vector for Canonical + fallback.
            } else result=current.search(q,3);
            assertEquals(expected,result.stream().map(SearchResultResponse::getChunkId).toList());assertEquals(1,result.getFirst().getScore(),1e-6);
        }
        double after=(System.nanoTime()-start)/1e6;
        assertEquals(3,off.stats().embeddingCalls());assertEquals(1,on.stats().embeddingCalls());assertEquals(2,on.stats().hits());
        assertArrayEquals(vector(),EmbeddingVectorCodec.decode(store.get(on.key(q)),1024,on.signature(),EmbeddingVectorCodec.sha(q)));
        String mode=prepared?"V2":"V1";
        String report=String.format(Locale.ROOT,"{\"mode\":\"%s\",\"queries_per_arm\":3,\"calls_before\":3,\"calls_after\":1,\"total_ms_before\":%.3f,\"total_ms_after\":%.3f,\"hits\":2,\"misses\":1,\"provider\":\"deterministic stub with 30ms delay; NOT live provider performance\"}",mode,before,after);
        Files.createDirectories(Path.of("target"));Files.writeString(Path.of("target/query-cache-performance-"+mode+".json"),report);
        System.out.println("QUERY_CACHE_LOCAL_MEASUREMENT "+report);
    }
    @ParameterizedTest @ValueSource(strings={"v1","canonical","fallback"})
    void cacheHitDoesNotCacheAnswersAndCitationIsUnchanged(String mode) {
        var refs=mock(EvidenceRefRepository.class);var ref=new EvidenceRef();var chunk=new Chunk();chunk.setId(22L);ref.setCanonicalChunk(chunk);ref.setEvidenceDocument(evidence);ref.setFactIndex(0);ref.setEvidenceText("Synthetic fixture fact.");
        when(refs.findByCanonicalChunk_IdInOrderByCanonicalChunk_IdAscFactIndexAscIdAsc(anyCollection())).thenReturn(List.of(ref));
        var check=mock(AnswerabilityService.class);when(check.check(anyString(),anyString(),anyList())).thenAnswer(call->{
            List<SourceResponse> sources=call.getArgument(2);
            return mode.equals("fallback") && sources.getFirst().getChunkId()==22L?AnswerabilityResult.notAnswerable("isolated fallback fixture"):new AnswerabilityResult(true,List.of("S1"),"isolated");
        });
        var chat=mock(ChatModel.class);when(chat.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("Synthetic fixture answer [S1].")))));
        var old=new RagService(retrieval(false),check,chat,documents,refs,!mode.equals("v1"),readiness);
        var current=new RagService(retrieval(true),check,chat,documents,refs,!mode.equals("v1"),readiness);
        var before=old.ask("synthetic query");var first=current.ask("synthetic query");var second=current.ask("synthetic query");
        assertEquals(before.getAnswer(),first.getAnswer());assertEquals(first.getAnswer(),second.getAnswer());
        assertEquals(List.of(1L),second.getSources().stream().map(SourceResponse::getDocumentId).toList());
        assertEquals(before.getSources().getFirst().getChunkId(),second.getSources().getFirst().getChunkId());
        assertEquals("PERSONAL",second.getSources().getFirst().getSourceType());verify(chat,times(3)).call(any(Prompt.class));
        assertEquals(1,on.stats().embeddingCalls());assertEquals(1,on.stats().hits());
    }
    @ParameterizedTest @ValueSource(booleans={false,true})
    void cacheHitStillChecksReadinessAndCannotPublishFailedEvidence(boolean prepared) {
        var service=retrieval(true);String q="readiness query";
        assertEquals(1,(prepared?service.searchEvidence(service.prepareCanonicalFirst(q),3):service.search(q,3)).size());
        when(ready.getStatus()).thenReturn(ImportTaskStatus.FAILED);
        assertTrue((prepared?service.searchEvidence(service.prepareCanonicalFirst(q),3):service.search(q,3)).isEmpty());
        assertEquals(1,on.stats().embeddingCalls());assertEquals(1,on.stats().hits());verify(documents,atLeast(2)).findReadinessByIds(anyCollection());
    }
}
