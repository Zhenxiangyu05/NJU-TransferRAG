package com.yu.transferrag.service;

import com.yu.transferrag.entity.*;
import com.yu.transferrag.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.test.context.TestPropertySource;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import io.qdrant.client.*;
import io.qdrant.client.grpc.Points.UpsertPoints;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import com.sun.net.httpserver.HttpServer;
import javax.sql.DataSource;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real SQL authority + a real delayed network request forwarded to real Qdrant. */
@Testcontainers(disabledWithoutDocker=true)
@SpringJUnitConfig(FrozenEvidenceIntegrationTest.Config.class)
@TestPropertySource(properties={"spring.ai.vectorstore.qdrant.collection-name=isolated_frozen",
    "spring.ai.openai.embedding.model=isolated-model","app.import-tasks.lease-seconds=90"})
class FrozenEvidenceIntegrationTest {
    @Container static final MySQLContainer MYSQL=new MySQLContainer("mysql:8.0.44")
        .withDatabaseName("isolated_frozen").withUsername("test").withPassword("isolated-test-only");
    @Container static final GenericContainer<?> QDRANT=new GenericContainer<>("qdrant/qdrant:v1.18.0")
        .withExposedPorts(6333,6334).waitingFor(Wait.forHttp("/readyz").forPort(6333));
    @Configuration @EnableTransactionManagement @EnableJpaRepositories(basePackageClasses=DocumentRepository.class)
    @Import({EvidenceRegistrationService.class,ImportTaskStateService.class,EvidenceChunkPersistenceService.class,
        EvidenceSnapshotStateService.class,VectorIndexService.class,ImportTaskManualGuard.class,EvidenceReadinessService.class,FrozenEvidenceIndexer.class})
    static class Config {
        @Bean DataSource dataSource() { return new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword()); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("com.yu.transferrag.entity");
            f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop",
                "hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));return f;
        }
        @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory f) { return new JpaTransactionManager(f); }
        @Bean(destroyMethod="close") QdrantClient client() { return new QdrantClient(QdrantGrpcClient.newBuilder(QDRANT.getHost(),QDRANT.getMappedPort(6334),false).build()); }
        @Bean EmbeddingModel embedding() {
            var model=mock(EmbeddingModel.class);when(model.dimensions()).thenReturn(3);
            when(model.embed(anyList(),any(),any())).thenAnswer(i -> ((List<?>)i.getArgument(0)).stream().map(x -> new float[]{1,0,0}).toList());
            when(model.embed(org.mockito.ArgumentMatchers.<String>anyList())).thenAnswer(i -> ((List<?>)i.getArgument(0)).stream().map(x -> new float[]{1,0,0}).toList());
            return model;
        }
        @Bean VectorStore vectorStore(QdrantClient client,EmbeddingModel embedding) throws Exception {
            var store=QdrantVectorStore.builder(client,embedding).collectionName("isolated_frozen").initializeSchema(true).build();store.afterPropertiesSet();return store;
        }
    }
    @Autowired EvidenceRegistrationService registration;
    @Autowired ImportTaskStateService state;
    @Autowired EvidenceChunkPersistenceService persistence;
    @Autowired EvidenceSnapshotStateService snapshots;
    @Autowired FrozenEvidenceIndexer index;
    @Autowired EvidenceReadinessService readiness;
    @Autowired EvidenceIndexSnapshotRepository snapshotRows;
    @Autowired ChunkRepository chunks;
    @Autowired QdrantClient client;
    @Autowired EmbeddingModel embedding;
    @Autowired VectorStore store;
    @Autowired DataSource ds;
    @Autowired PlatformTransactionManager transactions;
    @Autowired VectorIndexService legacyIndex;

    EvidenceRegistrationService.Registration register() {
        var d=new Document();d.setTitle("isolated immutable fact");d.setYear(2026);d.setDepartment("isolated department");d.setFileHash(UUID.randomUUID().toString());
        return registration.register(d);
    }
    void checkpoint(ImportTaskStateService.Lease lease) {
        state.stage(lease,ImportTaskStage.CHUNKING);var ch=new Chunk();ch.setChunkIndex(0);ch.setContent("isolated original fact");
        persistence.persistFirst(lease,List.of(ch));
    }
    @Test void delayedOldNetworkWriteAfterNewOwnerSucceededReplaysIdenticalDurablePoints() throws Exception {
        clearInvocations(embedding);
        var r=register();var a=state.claim(r.taskId(),"old-owner").orElseThrow();checkpoint(a);
        var tx=new TransactionTemplate(transactions);
        var ids=tx.execute(s -> chunks.findByDocument_IdOrderByChunkIndexAsc(r.documentId()).stream().map(Chunk::getId).toList());
        long countBefore=client.countAsync("isolated_frozen").get(10,TimeUnit.SECONDS);
        var accepted=new CountDownLatch(1);var release=new CountDownLatch(1);
        var proxy=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        var proxyPool=Executors.newSingleThreadExecutor();proxy.setExecutor(proxyPool);
        proxy.createContext("/delayed-upsert",exchange -> {
            try {
                var request=UpsertPoints.parseFrom(exchange.getRequestBody().readAllBytes());accepted.countDown();
                if(!release.await(45,TimeUnit.SECONDS)) throw new IllegalStateException();
                client.upsertAsync(request).get(10,TimeUnit.SECONDS);
                exchange.sendResponseHeaders(200,-1);
            } catch(Exception failure) { exchange.sendResponseHeaders(500,-1); }
            finally { exchange.close(); }
        });
        proxy.start();var executor=Executors.newSingleThreadExecutor();
        try {
            var oldIndex=new FrozenEvidenceIndexer(snapshots,embedding,store,"isolated_frozen","isolated-model") {
                @Override protected void write(EvidenceSnapshotCodec.Wire wire) {
                    var bytes=UpsertPoints.newBuilder().setCollectionName(wire.collection()).setWait(true)
                        .addAllPoints(EvidenceSnapshotCodec.points(wire)).build().toByteArray();
                    try {
                        int status=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+proxy.getAddress().getPort()+"/delayed-upsert"))
                            .timeout(java.time.Duration.ofSeconds(50)).POST(HttpRequest.BodyPublishers.ofByteArray(bytes)).build(),HttpResponse.BodyHandlers.discarding()).statusCode();
                        if(status!=200) throw new IllegalStateException();
                    } catch(Exception failure) { throw new IllegalStateException("test relay failed",failure); }
                }
            };
            var pending=executor.submit(() -> oldIndex.index(a));assertTrue(accepted.await(20,TimeUnit.SECONDS));
            assertTrue(readiness.readyDocumentIds(List.of(r.documentId())).isEmpty());
            var frozen=snapshots.load(a);assertNotNull(frozen.wire());
            var sql=new JdbcTemplate(ds);
            sql.update("UPDATE import_task SET lease_until=CURRENT_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",r.taskId());
            state.recoverExpired(Set.of());var b=state.claim(r.taskId(),"new-owner").orElseThrow();
            // Deliberate isolated out-of-band mutation proves retries DO NOT reread metadata/content.
            sql.update("UPDATE document SET department='changed isolated metadata' WHERE id=?",r.documentId());
            sql.update("UPDATE chunk SET content='changed isolated content' WHERE document_id=?",r.documentId());
            int completed=index.index(b);state.succeed(b,completed,completed);
            assertEquals("SUCCEEDED",state.view(r.taskId(),false).getStatus());
            assertEquals(Set.of(r.documentId()),readiness.readyDocumentIds(List.of(r.documentId())));
            release.countDown();assertEquals(1,pending.get(20,TimeUnit.SECONDS));
            assertThrows(ImportTaskStateService.LostLease.class,() -> state.succeed(a,1,1));
            var expected=EvidenceSnapshotCodec.points(frozen.wire()).getFirst();
            var actual=client.retrieveAsync("isolated_frozen",expected.getId(),true,true,null).get(10,TimeUnit.SECONDS).getFirst();
            assertEquals(expected.getPayloadMap(),actual.getPayloadMap());
            assertEquals(expected.getVectors().getVector().getDense().getDataList(),actual.getVectors().getVector().getDense().getDataList());
            assertEquals(countBefore+1,client.countAsync("isolated_frozen").get(10,TimeUnit.SECONDS));
            assertEquals(ids,tx.execute(s -> chunks.findByDocument_IdOrderByChunkIndexAsc(r.documentId()).stream().map(Chunk::getId).toList()));
            verify(embedding,times(1)).embed(org.mockito.ArgumentMatchers.<String>anyList());
            assertEquals("SUCCEEDED",state.view(r.taskId(),false).getStatus());
        } finally { release.countDown();executor.shutdownNow();proxy.stop(0);proxyPool.shutdownNow(); }
    }
    @Test void failedWriteAndRetryNeverReembedAndManualMutationIsDenied() {
        clearInvocations(embedding);var r=register();var a=state.claim(r.taskId(),"owner").orElseThrow();checkpoint(a);
        var failing=new FrozenEvidenceIndexer(snapshots,embedding,store,"isolated_frozen","isolated-model") {
            @Override protected void write(EvidenceSnapshotCodec.Wire wire) { throw new IllegalStateException("isolated transport failure"); }
        };
        assertThrows(IllegalStateException.class,() -> failing.index(a));state.fail(a,"ISOLATED_FAILURE",true);
        assertTrue(readiness.readyDocumentIds(List.of(r.documentId())).isEmpty());
        state.retry(r.taskId());var b=state.claim(r.taskId(),"new-owner").orElseThrow();
        assertEquals(1,index.index(b));state.succeed(b,1,1);
        verify(embedding,times(1)).embed(org.mockito.ArgumentMatchers.<String>anyList());
        assertThrows(org.springframework.web.server.ResponseStatusException.class,() -> legacyIndex.indexDocument(r.documentId()));
        assertThrows(org.springframework.web.server.ResponseStatusException.class,() -> legacyIndex.removeDocumentIndex(r.documentId()));
    }
    @Test void modelChangeAndCorruptSnapshotFailBeforeAnyExternalWrite() {
        var r=register();var a=state.claim(r.taskId(),"owner").orElseThrow();checkpoint(a);clearInvocations(embedding);
        var wrong=new FrozenEvidenceIndexer(snapshots,embedding,store,"isolated_frozen","changed-model");
        assertThrows(EvidenceImportProcessor.StageFailure.class,() -> wrong.index(a));
        new JdbcTemplate(ds).update("UPDATE evidence_index_snapshot SET input_sha256='invalid' WHERE task_id=?",r.taskId());
        assertThrows(EvidenceImportProcessor.StageFailure.class,() -> index.index(a));
        verify(embedding,never()).embed(org.mockito.ArgumentMatchers.<String>anyList());
    }
    @Test void expiredEmbeddingOwnerCannotPublishACompetingVectorArtifact() throws Exception {
        var r=register();var a=state.claim(r.taskId(),"embedding-old").orElseThrow();checkpoint(a);
        var started=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
        var slow=mock(EmbeddingModel.class);
        when(slow.embed(org.mockito.ArgumentMatchers.<String>anyList())).thenAnswer(i -> {
            started.countDown();if(!release.await(30,TimeUnit.SECONDS)) throw new IllegalStateException();
            return List.of(new float[]{0,1,0});
        });
        try {
            var old=new FrozenEvidenceIndexer(snapshots,slow,store,"isolated_frozen","isolated-model");
            var pending=pool.submit(() -> old.index(a));assertTrue(started.await(10,TimeUnit.SECONDS));
            new JdbcTemplate(ds).update("UPDATE import_task SET lease_until=CURRENT_TIMESTAMP(6)-INTERVAL 1 SECOND WHERE id=?",r.taskId());
            state.recoverExpired(Set.of());var b=state.claim(r.taskId(),"embedding-new").orElseThrow();
            assertEquals(1,index.index(b));state.succeed(b,1,1);
            String hash=snapshotRows.findById(r.taskId()).orElseThrow().getPointsSha256();
            release.countDown();var error=assertThrows(ExecutionException.class,() -> pending.get(10,TimeUnit.SECONDS));
            assertInstanceOf(ImportTaskStateService.LostLease.class,error.getCause());
            assertEquals(hash,snapshotRows.findById(r.taskId()).orElseThrow().getPointsSha256());
            assertEquals("SUCCEEDED",state.view(r.taskId(),false).getStatus());
        } finally {release.countDown();pool.shutdownNow();}
    }
}
