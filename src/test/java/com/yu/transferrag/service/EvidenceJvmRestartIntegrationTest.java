package com.yu.transferrag.service;

import com.yu.transferrag.entity.*;
import com.yu.transferrag.repository.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import io.qdrant.client.*;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.*;
import javax.sql.DataSource;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.BooleanSupplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Real JVM crash + real MySQL/Qdrant. No application.properties, HTTP port or external AI provider. */
@Testcontainers(disabledWithoutDocker = true)
class EvidenceJvmRestartIntegrationTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.44")
            .withDatabaseName("isolated_jvm_restore").withUsername("test").withPassword("isolated-test-only");
    @Container static final GenericContainer<?> QDRANT = new GenericContainer<>("qdrant/qdrant:v1.18.0")
            .withExposedPorts(6333,6334).waitingFor(Wait.forHttp("/readyz").forPort(6333));
    @TempDir(cleanup = org.junit.jupiter.api.io.CleanupMode.ON_SUCCESS) Path directory;
    static final String COLLECTION = "isolated_jvm_restore";

    @Configuration @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = DocumentRepository.class)
    @Import({ImportTaskStateService.class,EvidenceRegistrationService.class,EvidenceChunkPersistenceService.class,
            EvidenceReadinessService.class, EvidenceSnapshotStateService.class, ImportTaskManualGuard.class})
    static class DatabaseConfig {
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds,
                org.springframework.core.env.Environment env) {
            var f = new LocalContainerEntityManagerFactoryBean(); f.setDataSource(ds);
            f.setPackagesToScan("com.yu.transferrag.entity"); f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto",env.getRequiredProperty("test.ddl"),
                    "hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));
            return f;
        }
        @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory f) {
            return new JpaTransactionManager(f);
        }
    }
    static AnnotationConfigApplicationContext context(String url,String user,String password,
            String host,int grpcPort,String ddl,boolean crash,Path marker) throws Exception {
        // Even a manually invoked child rejects non-test DB names and non-loopback endpoints.
        if (!(url.startsWith("jdbc:mysql://localhost:") || url.startsWith("jdbc:mysql://127.0.0.1:"))
                || !url.contains("/isolated_jvm_restore") || !(host.equals("localhost") || host.equals("127.0.0.1")))
            throw new IllegalArgumentException("Only isolated loopback test endpoints allowed");
        var c = new AnnotationConfigApplicationContext();
        c.getEnvironment().getPropertySources().addFirst(new org.springframework.core.env.MapPropertySource("test-only",
                Map.of("test.ddl",ddl,"app.import-tasks.lease-seconds","15","app.import-tasks.max-attempts","3",
                    "spring.ai.vectorstore.qdrant.collection-name",COLLECTION,"spring.ai.openai.embedding.model","isolated-model")));
        c.registerBean(DataSource.class,() -> new DriverManagerDataSource(url,user,password));
        var client = new QdrantClient(QdrantGrpcClient.newBuilder(host,grpcPort,false).build());
        c.registerBean(QdrantClient.class,() -> client);
        EmbeddingModel embedding = mock(EmbeddingModel.class);
        when(embedding.dimensions()).thenReturn(3);
        when(embedding.embed(anyList(),any(),any())).thenAnswer(i -> {
            List<org.springframework.ai.document.Document> documents=i.getArgument(0);
            return documents.stream().map(d -> new float[]{1,.2f,.1f}).toList();
        });
        when(embedding.embed(org.mockito.ArgumentMatchers.<String>anyList())).thenAnswer(i -> {
            if(ddl.equals("validate") && !crash) throw new AssertionError("Recovery must not re-embed frozen points");
            List<String> texts=i.getArgument(0);
            return texts.stream().map(text -> new float[]{1,.2f,.1f}).toList();
        });
        var store=QdrantVectorStore.builder(client,embedding).collectionName(COLLECTION).initializeSchema(true).build();
        store.afterPropertiesSet();
        c.registerBean(VectorIndexService.class,() -> new VectorIndexService(c.getBean(ChunkRepository.class),store));
        c.registerBean(FrozenEvidenceIndexer.class,() -> new FrozenEvidenceIndexer(c.getBean(EvidenceSnapshotStateService.class),
                embedding,store,COLLECTION,"isolated-model") {
            @Override protected void write(EvidenceSnapshotCodec.Wire wire) {
                if (crash) {
                    super.write(new EvidenceSnapshotCodec.Wire(wire.version(),wire.collection(),wire.model(),wire.dimension(),List.of(wire.points().getFirst())));
                    try {
                        Files.writeString(marker,"PARTIAL_INDEX_COMMITTED");
                        new CountDownLatch(1).await(); // Parent kills this JVM, not a Mockito restart.
                    } catch (Exception e) { throw new IllegalStateException("test interruption"); }
                }
                super.write(wire);
            }
        });
        c.register(DatabaseConfig.class); c.refresh(); return c;
    }
    static void await(BooleanSupplier condition,Duration limit) throws Exception {
        long until=System.nanoTime()+limit.toNanos();
        while (!condition.getAsBoolean()) {
            if (System.nanoTime()>until) throw new AssertionError("Test condition timed out");
            Thread.sleep(100);
        }
    }
    Process child(String mode,long taskId,Path marker) throws Exception {
        Path args=directory.resolve(mode+".args");
        String cp=System.getProperty("surefire.test.class.path",System.getProperty("java.class.path"));
        Files.writeString(args,"-Xmx256m\n-classpath\n\""+cp.replace("\\","\\\\").replace("\"","\\\"")+"\"\n");
        String executable=System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win") ? "java.exe" : "java";
        var builder=new ProcessBuilder(Path.of(System.getProperty("java.home"),"bin",executable).toString(),
                "@"+args,Child.class.getName(),mode,Long.toString(taskId));
        Map<String,String> env=builder.environment();
        env.put("NJU_TEST_JDBC_URL",MYSQL.getJdbcUrl()); env.put("NJU_TEST_SQL_USER",MYSQL.getUsername());
        env.put("NJU_TEST_SQL_PASSWORD",MYSQL.getPassword()); env.put("NJU_TEST_QDRANT_HOST",QDRANT.getHost());
        env.put("NJU_TEST_QDRANT_PORT",QDRANT.getMappedPort(6334).toString()); env.put("NJU_TEST_MARKER",marker.toString());
        return builder.redirectErrorStream(true).redirectOutput(directory.resolve(mode+".log").toFile()).start();
    }
    @Test void killedJvmRecoversPersistedCheckpointAndPublishesOnlyAfterSuccessfulUpsert() throws Exception {
        Path marker=directory.resolve("checkpoint"); Process first=null,second=null;
        try (var c=context(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword(),QDRANT.getHost(),
                QDRANT.getMappedPort(6334),"create",false,marker)) {
            var d=new Document(); d.setTitle("isolated JVM fixture"); d.setFileHash(UUID.randomUUID().toString());
            var registration=c.getBean(EvidenceRegistrationService.class).register(d);
            var state=c.getBean(ImportTaskStateService.class); var ready=c.getBean(EvidenceReadinessService.class);
            var tx=new TransactionTemplate(c.getBean(PlatformTransactionManager.class));
            var chunks=c.getBean(ChunkRepository.class); var tasks=c.getBean(ImportTaskRepository.class);
            first=child("crash",registration.taskId(),marker);
            Process processA=first;
            await(() -> Files.exists(marker) || !processA.isAlive(),Duration.ofSeconds(90));
            assertTrue(Files.exists(marker),"Child A exited before checkpoint; see test-only crash.log");
            var before=tx.execute(s -> tasks.findById(registration.taskId()).orElseThrow());
            var ids=tx.execute(s -> chunks.findByDocument_IdOrderByChunkIndexAsc(registration.documentId()).stream().map(Chunk::getId).toList());
            var frozenBefore=c.getBean(EvidenceIndexSnapshotRepository.class).findById(registration.taskId()).orElseThrow();
            assertNotNull(frozenBefore.getPointsJson());
            assertEquals(2,ids.size()); assertEquals(ImportTaskStatus.PROCESSING,before.getStatus());
            assertEquals(ImportTaskStage.INDEXING,before.getStage()); assertEquals(1,before.getAttemptCount());
            assertTrue(ready.readyDocumentIds(List.of(registration.documentId())).isEmpty());
            assertEquals(1L,c.getBean(QdrantClient.class).countAsync(COLLECTION).get(10,TimeUnit.SECONDS));
            long firstPid=first.pid(); first.destroyForcibly(); assertTrue(first.waitFor(15,TimeUnit.SECONDS));
            second=child("recover",registration.taskId(),marker); assertNotEquals(firstPid,second.pid());
            assertTrue(second.waitFor(100,TimeUnit.SECONDS)); assertEquals(0,second.exitValue(),"See test-only recover.log");
            var after=tx.execute(s -> tasks.findById(registration.taskId()).orElseThrow());
            assertEquals(ImportTaskStatus.SUCCEEDED,after.getStatus()); assertEquals(2,after.getAttemptCount());
            assertTrue(after.getLeaseGeneration()>before.getLeaseGeneration());
            assertEquals(ids,tx.execute(s -> chunks.findByDocument_IdOrderByChunkIndexAsc(registration.documentId()).stream().map(Chunk::getId).toList()));
            assertEquals(2L,c.getBean(QdrantClient.class).countAsync(COLLECTION).get(10,TimeUnit.SECONDS));
            var frozenAfter=c.getBean(EvidenceIndexSnapshotRepository.class).findById(registration.taskId()).orElseThrow();
            assertEquals(frozenBefore.getInputSha256(),frozenAfter.getInputSha256());
            assertEquals(frozenBefore.getPointsSha256(),frozenAfter.getPointsSha256());
            var wire=EvidenceSnapshotCodec.decode(frozenAfter.getPointsJson(),frozenAfter.getPointsSha256(),EvidenceSnapshotCodec.Wire.class);
            for(var expected:EvidenceSnapshotCodec.points(wire)) {
                var actual=c.getBean(QdrantClient.class).retrieveAsync(COLLECTION,expected.getId(),true,true,null).get(10,TimeUnit.SECONDS).getFirst();
                assertEquals(expected.getPayloadMap(),actual.getPayloadMap());
                var actualVector=actual.getVectors().getVector().getDense().getDataList();
                var expectedVector=expected.getVectors().getVector().getDense().getDataList();
                // Qdrant cosine normalizes stored vectors; compare mathematically normalized frozen input.
                double norm=Math.sqrt(expectedVector.stream().mapToDouble(v -> v*v).sum());
                for(int n=0;n<expectedVector.size();n++) assertEquals(expectedVector.get(n)/norm,actualVector.get(n),1e-6);
            }
            assertEquals(Set.of(registration.documentId()),ready.readyDocumentIds(List.of(registration.documentId())));
            System.out.printf("JVM recovery verified: terminatedPid=%d recoveryPid=%d attempts=%d stableChunks=%d points=2%n",
                    firstPid,second.pid(),after.getAttemptCount(),ids.size());
        } finally {
            // Only processes started by this test, never other project JVMs or Docker containers.
            if(first!=null && first.isAlive()) { first.destroyForcibly(); first.waitFor(15,TimeUnit.SECONDS); }
            if(second!=null && second.isAlive()) { second.destroyForcibly(); second.waitFor(15,TimeUnit.SECONDS); }
        }
    }
    public static class Child {
        public static void main(String[] args) throws Exception {
            boolean crash=args[0].equals("crash"); long id=Long.parseLong(args[1]);
            Path marker=Path.of(System.getenv("NJU_TEST_MARKER"));
            try (var c=context(System.getenv("NJU_TEST_JDBC_URL"),System.getenv("NJU_TEST_SQL_USER"),
                    System.getenv("NJU_TEST_SQL_PASSWORD"),System.getenv("NJU_TEST_QDRANT_HOST"),
                    Integer.parseInt(System.getenv("NJU_TEST_QDRANT_PORT")),"validate",crash,marker)) {
                var state=c.getBean(ImportTaskStateService.class);
                ChunkService parser=mock(ChunkService.class);
                when(parser.prepareEvidenceChunks(anyLong())).thenAnswer(i -> {
                    if (!crash) throw new AssertionError("Restart must reuse the persisted INDEXING checkpoint");
                    List<Chunk> chunks=new ArrayList<>();
                    for(int n=0;n<2;n++) { var ch=new Chunk();ch.setChunkIndex(n);ch.setContent("isolated fact "+n);chunks.add(ch); }
                    return chunks;
                });
                var processor=new EvidenceTaskProcessor(state,parser,c.getBean(EvidenceChunkPersistenceService.class),
                        c.getBean(FrozenEvidenceIndexer.class),mock(EvidenceLegacyVerifier.class));
                var worker=new EvidenceImportWorker(state,processor,180);
                var executor=new ThreadPoolExecutor(1,1,0,TimeUnit.SECONDS,new ArrayBlockingQueue<>(1),new ThreadPoolExecutor.AbortPolicy());
                var dispatcher=new ImportTaskDispatcher(state,worker,executor);
                try {
                    long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(75);
                    while(System.nanoTime()<until) {
                        dispatcher.scan(); dispatcher.heartbeat();
                        String status=state.view(id,false).getStatus();
                        if(status.equals("SUCCEEDED")) return;
                        if(status.equals("FAILED")) throw new AssertionError("Recovery task failed");
                        Thread.sleep(200);
                    }
                    throw new AssertionError("Recovery did not finish within deadline");
                } finally { executor.shutdownNow(); executor.awaitTermination(5,TimeUnit.SECONDS); }
            }
        }
    }
}
