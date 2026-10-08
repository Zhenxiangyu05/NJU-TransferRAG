package com.yu.transferrag.service;

import com.yu.transferrag.repository.ChunkRepository;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import io.qdrant.client.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@Testcontainers(disabledWithoutDocker = true)
class EvidenceQdrantIntegrationTest {
    @Container static final GenericContainer<?> QDRANT = new GenericContainer<>("qdrant/qdrant:v1.18.0")
            .withExposedPorts(6333,6334).waitingFor(Wait.forHttp("/readyz").forPort(6333));
    @Test void realQdrantStableUpsertPreservesExistingPointsAndRecoversPartialWrites() throws Exception {
        // A test-only collection on a disposable container; no application/Qdrant environment config is read.
        String collection = "isolated_evidence_" + UUID.randomUUID().toString().replace("-","");
        EmbeddingModel embedding = mock(EmbeddingModel.class);
        when(embedding.dimensions()).thenReturn(3);
        when(embedding.embed(anyList(),any(),any())).thenAnswer(call -> {
            List<Document> docs = call.getArgument(0);
            return docs.stream().map(d -> new float[]{1f,0.2f,0.1f}).toList();
        });
        try (var client = new QdrantClient(QdrantGrpcClient.newBuilder(QDRANT.getHost(),QDRANT.getMappedPort(6334),false).build())) {
            var store = QdrantVectorStore.builder(client,embedding).collectionName(collection).initializeSchema(true).build();
            store.afterPropertiesSet();
            Document original = Document.builder().id(EvidenceChunkPersistenceService.pointId(99,99)).text("existing point").build();
            Document first = Document.builder().id(EvidenceChunkPersistenceService.pointId(1,2)).text("first")
                .metadata("documentId","1").metadata("chunkId","2").metadata("documentRole","EVIDENCE").build();
            Document second = Document.builder().id(EvidenceChunkPersistenceService.pointId(1,3)).text("second")
                .metadata("documentId","1").metadata("chunkId","3").metadata("documentRole","EVIDENCE").build();
            store.add(List.of(original));
            store.add(List.of(first)); // Raw-client control: interrupted attempt prefix, not the new Worker path.
            assertEquals(2L,client.countAsync(collection).get(10,TimeUnit.SECONDS));
            store.add(List.of(first,second));
            store.add(List.of(first,second));
            assertEquals(3L,client.countAsync(collection).get(10,TimeUnit.SECONDS));
            var reader = new QdrantMetadataReader(org.springframework.web.client.RestClient.builder()
                .baseUrl("http://" + QDRANT.getHost() + ":" + QDRANT.getMappedPort(6333)).build(),collection);
            assertEquals(2,reader.findByDocumentId(1L).size());
            doThrow(new IllegalStateException("test embedding failure")).when(embedding).embed(anyList(),any(),any());
            assertThrows(RuntimeException.class, () -> store.add(List.of(first,second)));
            assertEquals(3L,client.countAsync(collection).get(10,TimeUnit.SECONDS));
        }
    }
    @Test void staleExternalUpsertCanOverwriteNewerContentWithoutQdrantFencing() throws Exception {
        String collection = "isolated_stale_writer_" + UUID.randomUUID().toString().replace("-","");
        EmbeddingModel embedding = mock(EmbeddingModel.class);
        when(embedding.dimensions()).thenReturn(3);
        when(embedding.embed(anyList(),any(),any())).thenAnswer(call -> {
            List<Document> docs = call.getArgument(0);
            return docs.stream().map(d -> new float[]{1f,0.2f,0.1f}).toList();
        });
        try (var client = new QdrantClient(QdrantGrpcClient.newBuilder(QDRANT.getHost(),QDRANT.getMappedPort(6334),false).build())) {
            var store = QdrantVectorStore.builder(client,embedding).collectionName(collection).initializeSchema(true).build();
            store.afterPropertiesSet();
            String id = EvidenceChunkPersistenceService.pointId(1,2);
            var newer = Document.builder().id(id).text("new snapshot").metadata("leaseGeneration",2).build();
            var stale = Document.builder().id(id).text("old snapshot").metadata("leaseGeneration",1).build();
            store.add(List.of(newer));
            store.add(List.of(stale)); // Raw-client control: Qdrant itself has no version fence.
            var rest = org.springframework.web.client.RestClient.builder()
                .baseUrl("http://" + QDRANT.getHost() + ":" + QDRANT.getMappedPort(6333)).build();
            Map<?,?> response = rest.post().uri("/collections/{collection}/points",collection)
                .body(Map.of("ids",List.of(id),"with_payload",true,"with_vector",false)).retrieve().body(Map.class);
            Map<?,?> point = (Map<?,?>)((List<?>)response.get("result")).getFirst();
            Map<?,?> payload = (Map<?,?>)point.get("payload");
            assertEquals("old snapshot",payload.get(QdrantVectorStore.DEFAULT_CONTENT_FIELD_NAME));
            assertEquals(1,((Number)payload.get("leaseGeneration")).intValue());
            assertEquals(1L,client.countAsync(collection).get(10,TimeUnit.SECONDS));
            // Characterization only: generation metadata alone is not a conditional-write fence.
        }
    }
}
