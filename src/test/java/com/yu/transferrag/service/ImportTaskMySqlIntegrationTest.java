package com.yu.transferrag.service;

import com.yu.transferrag.entity.*;
import com.yu.transferrag.repository.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.ai.vectorstore.VectorStore;
import org.testcontainers.mysql.MySQLContainer;
import org.testcontainers.junit.jupiter.*;
import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Never loads application/local datasource settings. Only this container can supply the JDBC URL. */
@Testcontainers(disabledWithoutDocker = true)
@SpringJUnitConfig(ImportTaskMySqlIntegrationTest.Config.class)
class ImportTaskMySqlIntegrationTest {
    @Container static final MySQLContainer MYSQL = new MySQLContainer("mysql:8.0.44")
            .withDatabaseName("isolated_import_tasks").withUsername("test").withPassword("isolated-test-only");

    @Configuration
    @EnableTransactionManagement
    @EnableJpaRepositories(basePackageClasses = DocumentRepository.class)
    @Import({ImportTaskStateService.class, EvidenceRegistrationService.class, EvidenceChunkPersistenceService.class,
             VectorIndexService.class, EvidenceReadinessService.class, ImportTaskManualGuard.class, EvidenceSnapshotStateService.class})
    static class Config {
        @Bean DataSource dataSource() {
            // No fallback URL, environment secrets, or external database credentials.
            return new DriverManagerDataSource(MYSQL.getJdbcUrl(),MYSQL.getUsername(),MYSQL.getPassword());
        }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource dataSource) {
            var factory = new LocalContainerEntityManagerFactoryBean();
            factory.setDataSource(dataSource);
            factory.setPackagesToScan("com.yu.transferrag.entity");
            factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop",
                "hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));
            return factory;
        }
        @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory factory) {
            return new JpaTransactionManager(factory);
        }
        @Bean VectorStore vectorStore() { return mock(VectorStore.class); }
    }
    @Autowired EvidenceRegistrationService registration;
    @Autowired ImportTaskStateService state;
    @Autowired EvidenceChunkPersistenceService persistence;
    @Autowired DocumentRepository documents;
    @Autowired ImportTaskRepository tasks;
    @Autowired ChunkRepository chunks;
    @Autowired PlatformTransactionManager transactions;
    @Autowired DataSource dataSource;
    @Autowired EvidenceReadinessService readiness;
    @Autowired EvidenceIndexSnapshotRepository snapshots;

    Document candidate(String hash) {
        Document d = new Document(); d.setTitle("isolated test"); d.setFileHash(hash);
        d.setDocumentRole(DocumentRole.EVIDENCE); d.setFilePath("test-only.md"); return d;
    }
    EvidenceRegistrationService.Registration register() { return registration.register(candidate(UUID.randomUUID().toString())); }
    Chunk chunk() { Chunk c = new Chunk(); c.setChunkIndex(0); c.setContent("isolated test content"); return c; }
    void expire(long id) { new JdbcTemplate(dataSource).update("UPDATE import_task SET lease_until = CURRENT_TIMESTAMP(6) - INTERVAL 1 SECOND WHERE id = ?",id); }
    List<Chunk> rows(long documentId) {
        return new TransactionTemplate(transactions).execute(status -> chunks.findByDocument_IdOrderByChunkIndexAsc(documentId));
    }
    <T> List<T> concurrently(Callable<T> call) throws Exception {
        var start = new CountDownLatch(1); var pool = Executors.newFixedThreadPool(2);
        try {
            Callable<T> wrapped = () -> { assertTrue(start.await(5,TimeUnit.SECONDS)); return call.call(); };
            var first = pool.submit(wrapped); var second = pool.submit(wrapped); start.countDown();
            return List.of(first.get(20,TimeUnit.SECONDS),second.get(20,TimeUnit.SECONDS));
        } finally { pool.shutdownNow(); }
    }
    @Test void sameHashConcurrentRegistrationCreatesExactlyOneDocumentAndTask() throws Exception {
        String hash = UUID.randomUUID().toString();
        var results = concurrently(() -> {
            try { return registration.register(candidate(hash)); }
            catch (org.springframework.dao.DataIntegrityViolationException conflict) { return registration.resolveHash(hash); }
        });
        assertEquals(results.get(0).documentId(),results.get(1).documentId());
        assertEquals(results.get(0).taskId(),results.get(1).taskId());
        assertEquals(1, new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM document WHERE file_hash = ?",Integer.class,hash));
        assertEquals(1, new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM import_task WHERE document_id = ?",Integer.class,results.getFirst().documentId()));
    }
    @Test void documentAndTaskRollbackTogether() {
        String hash = UUID.randomUUID().toString(); long beforeTasks = tasks.count();
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).execute(s -> {
            registration.register(candidate(hash)); throw new IllegalStateException("force rollback");
        }));
        assertTrue(documents.findByFileHash(hash).isEmpty()); assertEquals(beforeTasks,tasks.count());
    }
    @Test void onlyOneConcurrentOwnerCanClaim() throws Exception {
        var r = register();
        var results = concurrently(() -> state.claim(r.taskId(),UUID.randomUUID().toString()).isPresent());
        assertEquals(1,results.stream().filter(Boolean::booleanValue).count());
    }
    @Test void chunkRowsAndIndexCheckpointRollbackTogether() {
        var r = register(); var lease = state.claim(r.taskId(),"owner").orElseThrow();
        state.stage(lease,ImportTaskStage.CHUNKING);
        assertThrows(IllegalStateException.class, () -> new TransactionTemplate(transactions).execute(s -> {
            persistence.persistFirst(lease,List.of(chunk())); throw new IllegalStateException("force rollback");
        }));
        assertTrue(rows(r.documentId()).isEmpty()); assertEquals("CHUNKING",state.view(r.taskId(),false).getStage());
        assertTrue(snapshots.findById(r.taskId()).isEmpty());
    }
    @Test void indexingFailureRetryReusesChunkAndPointId() {
        var r = register(); var lease = state.claim(r.taskId(),"owner").orElseThrow();
        state.stage(lease,ImportTaskStage.CHUNKING); persistence.persistFirst(lease,List.of(chunk()));
        String pointId = persistence.snapshot(lease).vectors().getFirst().getId();
        var originalVector = persistence.snapshot(lease).vectors().getFirst();
        state.fail(lease,"EMBEDDING_OR_INDEX_FAILED",true); state.retry(r.taskId());
        var next = state.claim(r.taskId(),"next").orElseThrow();
        assertEquals(ImportTaskStage.INDEXING,next.stage());
        assertEquals(pointId,persistence.snapshot(next).vectors().getFirst().getId());
        var retryVector = persistence.snapshot(next).vectors().getFirst();
        assertEquals(originalVector.getText(),retryVector.getText());
        assertEquals(originalVector.getMetadata(),retryVector.getMetadata());
        assertEquals(1,rows(r.documentId()).size());
        state.succeed(next,1,1); assertEquals("SUCCEEDED",state.view(r.taskId(),false).getStatus());
    }
    @Test void leaseRecoveryFencesOldWorkerAndSkipsStillRunningLocalWorker() {
        var r = register(); var old = state.claim(r.taskId(),"old").orElseThrow(); expire(r.taskId());
        state.recoverExpired(Set.of(r.taskId())); assertEquals("PROCESSING",state.view(r.taskId(),false).getStatus());
        state.recoverExpired(Set.of()); var next = state.claim(r.taskId(),"new").orElseThrow();
        assertTrue(next.generation() > old.generation()); assertFalse(state.heartbeat(old));
        assertThrows(ImportTaskStateService.LostLease.class, () -> state.fail(old,"OLD",true));
        assertThrows(ImportTaskStateService.LostLease.class, () -> persistence.persistFirst(old,List.of(chunk())));
        assertEquals("PROCESSING",state.view(r.taskId(),false).getStatus()); assertTrue(rows(r.documentId()).isEmpty());
    }
    @Test void persistedQueueSurvivesRegistrationSchedulingCrashWindow() {
        var r = register();
        assertTrue(state.readyIds(1000).contains(r.taskId()));
        assertEquals("QUEUED",state.view(r.taskId(),true).getStatus());
    }
    @Test void simulatedWorkerRestartRecoversCommittedIndexCheckpoint() {
        var r = register(); var old = state.claim(r.taskId(),"terminated-worker").orElseThrow();
        state.stage(old,ImportTaskStage.CHUNKING); persistence.persistFirst(old,List.of(chunk())); expire(r.taskId());
        state.recoverExpired(Set.of());
        EvidenceImportProcessor processor = lease -> {
            var snapshot = persistence.snapshot(lease);
            return new EvidenceImportProcessor.Result(snapshot.vectors().size(),snapshot.vectors().size());
        };
        // New executor/owner after process loss; durable state, not an in-memory queue, determines the stage.
        new EvidenceImportWorker(state,processor,60).run(r.taskId());
        assertEquals("SUCCEEDED",state.view(r.taskId(),false).getStatus()); assertEquals(1,rows(r.documentId()).size());
    }
    @Test void retryBudgetAndFailedDuplicateArePersisted() {
        var r = register();
        for (int n = 0; n < 3; n++) {
            var lease = state.claim(r.taskId(),"owner").orElseThrow(); state.fail(lease,"PARSING_FAILED",true);
            assertEquals("FAILED",state.view(r.taskId(),true).getStatus());
            if (n < 2) state.retry(r.taskId());
        }
        assertFalse(state.view(r.taskId(),true).isRetryable());
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> state.retry(r.taskId()));
    }
    @Test void legacyRegistrationOnlyCreatesVerificationTask() {
        Document legacy = documents.saveAndFlush(candidate(UUID.randomUUID().toString()));
        var r = registration.resolveHash(legacy.getFileHash());
        assertTrue(r.duplicate()); assertEquals("VERIFY_EXISTING",state.view(r.taskId(),true).getStage());
        assertTrue(rows(legacy.getId()).isEmpty());
    }
    @Test void canonicalCannotBeRegisteredAsEvidenceTask() {
        Document d = candidate(UUID.randomUUID().toString()); d.setDocumentRole(DocumentRole.CANONICAL);
        documents.saveAndFlush(d);
        assertThrows(org.springframework.web.server.ResponseStatusException.class, () -> registration.resolveHash(d.getFileHash()));
        assertTrue(tasks.findByDocument_Id(d.getId()).isEmpty());
    }
    @Test void additiveMigrationIsRerunnableAndPreservesRows() {
        long documentCount = documents.count(), taskCount = tasks.count(), chunkCount = chunks.count();
        var sql = new ResourceDatabasePopulator(new ClassPathResource("sql/evidence-import-task-schema.sql"));
        sql.execute(dataSource); sql.execute(dataSource);
        var readinessSql = new ResourceDatabasePopulator(new ClassPathResource("sql/evidence-readiness-schema.sql"));
        readinessSql.execute(dataSource); readinessSql.execute(dataSource);
        assertEquals(documentCount,documents.count()); assertEquals(taskCount,tasks.count()); assertEquals(chunkCount,chunks.count());
        assertEquals(1,new JdbcTemplate(dataSource).queryForObject("SELECT COUNT(*) FROM information_schema.statistics WHERE table_schema = DATABASE() AND table_name = 'import_task' AND index_name = 'uk_import_task_document'",Integer.class));
    }
    @Test void parsingFailureAndDuplicateRetryHaveDurableTruth() {
        var r = register();
        ChunkService parser = mock(ChunkService.class);
        when(parser.prepareEvidenceChunks(r.documentId())).thenThrow(new IllegalStateException("simulated parse failure"));
        var processor = new EvidenceTaskProcessor(state,parser,persistence,mock(FrozenEvidenceIndexer.class),mock(EvidenceLegacyVerifier.class));
        new EvidenceImportWorker(state,processor,60).run(r.taskId());
        assertEquals("FAILED",state.view(r.taskId(),false).getStatus()); assertTrue(rows(r.documentId()).isEmpty());
        var duplicate = registration.register(documents.findById(r.documentId()).orElseThrow());
        assertEquals(r.taskId(),duplicate.taskId()); assertEquals("FAILED",state.view(duplicate.taskId(),true).getStatus());
        state.retry(r.taskId()); assertEquals("QUEUED",state.view(r.taskId(),true).getStatus());
    }
    @Test void embeddingFailureKeepsCommittedCheckpointForWorkerRetry() {
        var r = register();
        var failedIndex = mock(FrozenEvidenceIndexer.class);
        when(failedIndex.index(any())).thenThrow(new IllegalStateException("simulated embedding failure")).thenReturn(1);
        ChunkService parser = mock(ChunkService.class);
        when(parser.prepareEvidenceChunks(r.documentId())).thenReturn(List.of(chunk()));
        var processor = new EvidenceTaskProcessor(state,parser,persistence,failedIndex,mock(EvidenceLegacyVerifier.class));
        var worker = new EvidenceImportWorker(state,processor,60);
        worker.run(r.taskId());
        assertEquals("FAILED",state.view(r.taskId(),false).getStatus());
        assertEquals("INDEXING",state.view(r.taskId(),false).getStage()); assertEquals(1,rows(r.documentId()).size());
        Long chunkId = rows(r.documentId()).getFirst().getId();
        state.retry(r.taskId()); worker.run(r.taskId());
        assertEquals("SUCCEEDED",state.view(r.taskId(),false).getStatus());
        assertEquals(chunkId,rows(r.documentId()).getFirst().getId());
        verify(parser,times(1)).prepareEvidenceChunks(r.documentId());
    }

    @Test void realDatabaseReadinessHonorsImmutableOriginAndAllLifecycleStates() {
        var r = register();
        assertTrue(readiness.readyDocumentIds(List.of(r.documentId())).isEmpty());
        var lease = state.claim(r.taskId(),"owner").orElseThrow();
        assertTrue(readiness.readyDocumentIds(List.of(r.documentId())).isEmpty());
        state.stage(lease,ImportTaskStage.CHUNKING); persistence.persistFirst(lease,List.of(chunk()));
        state.fail(lease,"ISOLATED_INDEX_FAILURE",true);
        assertTrue(readiness.readyDocumentIds(List.of(r.documentId())).isEmpty());
        state.retry(r.taskId()); var next = state.claim(r.taskId(),"next").orElseThrow();
        state.succeed(next,1,1);
        assertEquals(Set.of(r.documentId()),readiness.readyDocumentIds(List.of(r.documentId())));
        Document legacy = documents.saveAndFlush(candidate(UUID.randomUUID().toString()));
        assertEquals(Set.of(legacy.getId()),readiness.readyDocumentIds(List.of(legacy.getId())));
        var verification = registration.resolveHash(legacy.getFileHash());
        var verifying = state.claim(verification.taskId(),"verify").orElseThrow();
        assertEquals(Set.of(legacy.getId()),readiness.readyDocumentIds(List.of(legacy.getId())));
        state.fail(verifying,"LEGACY_VERIFICATION_FAILED",false);
        assertEquals(Set.of(legacy.getId()),readiness.readyDocumentIds(List.of(legacy.getId())));
        new JdbcTemplate(dataSource).update("UPDATE import_task SET task_kind='UNKNOWN' WHERE id=?",r.taskId());
        assertTrue(readiness.readyDocumentIds(List.of(r.documentId())).isEmpty());
    }

    @Test void migrationAddsUnknownToPreexistingCompletedTaskWithoutStageInference() {
        // Separate container: construct the old schema without task_kind, never alter the shared test schema.
        try (var old = new MySQLContainer("mysql:8.0.44").withDatabaseName("isolated_old_task_schema")
                .withUsername("test").withPassword("isolated-test-only")) {
            old.start();
            var ds = new DriverManagerDataSource(old.getJdbcUrl(),old.getUsername(),old.getPassword());
            var sql = new JdbcTemplate(ds);
            sql.execute("CREATE TABLE document (id BIGINT NOT NULL PRIMARY KEY)");
            sql.update("INSERT INTO document (id) VALUES (1)");
            new ResourceDatabasePopulator(new ClassPathResource("sql/evidence-import-task-schema.sql")).execute(ds);
            sql.update("""
                    INSERT INTO import_task (document_id,status,stage,max_attempts,next_attempt_at,created_at,updated_at)
                    VALUES (1,'SUCCEEDED','COMPLETED',3,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))
                    """);
            var migration = new ResourceDatabasePopulator(new ClassPathResource("sql/evidence-readiness-schema.sql"));
            migration.execute(ds); migration.execute(ds);
            assertEquals("UNKNOWN",sql.queryForObject("SELECT task_kind FROM import_task WHERE document_id=1",String.class));
            assertEquals("COMPLETED",sql.queryForObject("SELECT stage FROM import_task WHERE document_id=1",String.class));
            assertEquals("SUCCEEDED",sql.queryForObject("SELECT status FROM import_task WHERE document_id=1",String.class));
            assertEquals(1,sql.queryForObject("SELECT COUNT(*) FROM document",Integer.class));
            assertEquals(1,sql.queryForObject("SELECT COUNT(*) FROM import_task",Integer.class));
        }
    }

    @Test void realYearAggregatesExcludeNewUnpublishedEvidenceButKeepLegacyVerification() {
        String department = "isolated-"+UUID.randomUUID();
        Document legacy = candidate(UUID.randomUUID().toString()); legacy.setDepartment(department); legacy.setYear(2020);
        documents.saveAndFlush(legacy);
        new TransactionTemplate(transactions).execute(s -> { Chunk c=chunk(); c.setDocument(legacy); chunks.saveAndFlush(c); return null; });
        var verification = registration.resolveHash(legacy.getFileHash());
        var verifying = state.claim(verification.taskId(),"verify").orElseThrow();
        state.fail(verifying,"LEGACY_VERIFICATION_FAILED",false);
        Integer before = chunks.findMaxEffectiveYear();
        Document recent = candidate(UUID.randomUUID().toString()); recent.setDepartment(department); recent.setYear(2099);
        var r = registration.register(recent); var lease = state.claim(r.taskId(),"owner").orElseThrow();
        state.stage(lease,ImportTaskStage.CHUNKING); persistence.persistFirst(lease,List.of(chunk()));
        assertEquals(before,chunks.findMaxEffectiveYear());
        assertEquals(2020,chunks.findMaxEffectiveYearForDepartmentsOrGlobalOfficial(List.of(department),"GLOBAL","OFFICIAL_PDF"));
        state.fail(lease,"INDEX_FAILED",true);
        assertEquals(2020,chunks.findMaxEffectiveYearForDepartmentsOrGlobalOfficial(List.of(department),"GLOBAL","OFFICIAL_PDF"));
        state.retry(r.taskId()); var next = state.claim(r.taskId(),"next").orElseThrow(); state.succeed(next,1,1);
        assertEquals(2099,chunks.findMaxEffectiveYearForDepartmentsOrGlobalOfficial(List.of(department),"GLOBAL","OFFICIAL_PDF"));
    }

    @Test void fullLegacyEntitySchemaUpgradeIsIdempotentAndHibernateValidatePreservesLegacyVisibility() throws Exception {
        // Reconstruct the local V2 entity-defined schema, not a copy of production data.
        var sourceSql=new JdbcTemplate(dataSource);
        var d=candidate(UUID.randomUUID().toString());documents.saveAndFlush(d);
        new TransactionTemplate(transactions).execute(s -> {var c=chunk();c.setDocument(d);chunks.saveAndFlush(c);return null;});
        try(var isolated=new MySQLContainer("mysql:8.0.44").withDatabaseName("isolated_legacy_upgrade")
                .withUsername("test").withPassword("isolated-test-only")) {
            isolated.start();
            var targetDs=new DriverManagerDataSource(isolated.getJdbcUrl(),isolated.getUsername(),isolated.getPassword());
            var sql=new JdbcTemplate(targetDs);
            sql.execute("SET FOREIGN_KEY_CHECKS=0");
            for(String table:List.of("document","chunk","evidence_ref","entity_alias")) {
                String ddl=sourceSql.queryForObject("SHOW CREATE TABLE "+table,(rs,n) -> rs.getString(2));sql.execute(ddl);
            }
            sql.execute("SET FOREIGN_KEY_CHECKS=1");
            for(String table:List.of("document","chunk")) {
                String predicate=table.equals("document") ? "id" : "document_id";
                for(Map<String,Object> row:sourceSql.queryForList("SELECT * FROM "+table+" WHERE "+predicate+"=?",d.getId())) {
                    List<String> columns=new ArrayList<>(row.keySet());
                    sql.update("INSERT INTO "+table+" ("+String.join(",",columns)+") VALUES ("+String.join(",",Collections.nCopies(columns.size(),"?"))+")",
                        columns.stream().map(row::get).toArray());
                }
            }
            var migration=new ResourceDatabasePopulator(new ClassPathResource("sql/evidence-import-task-schema.sql"),
                new ClassPathResource("sql/evidence-readiness-schema.sql"),new ClassPathResource("sql/evidence-index-snapshot-schema.sql"));
            migration.execute(targetDs);migration.execute(targetDs);
            assertEquals(1,sql.queryForObject("SELECT COUNT(*) FROM document",Integer.class));
            assertEquals(1,sql.queryForObject("SELECT COUNT(*) FROM chunk",Integer.class));
            assertEquals(0,sql.queryForObject("SELECT COUNT(*) FROM import_task",Integer.class));
            assertEquals(0,sql.queryForObject("SELECT COUNT(*) FROM evidence_index_snapshot",Integer.class));
            try(var ctx=new AnnotationConfigApplicationContext()) {
                ctx.registerBean(DataSource.class,() -> targetDs);
                ctx.register(ValidateLegacyConfig.class);ctx.refresh();
                var ready=ctx.getBean(EvidenceReadinessService.class);
                assertEquals(Set.of(d.getId()),ready.readyDocumentIds(List.of(d.getId())));
            }
            // Both foreign keys are real RESTRICT relationships, not just Hibernate metadata.
            assertThrows(org.springframework.dao.DataIntegrityViolationException.class,() -> sql.update(
                "INSERT INTO evidence_index_snapshot(task_id,input_json,input_sha256) VALUES (999,'{}',?)","0".repeat(64)));
        }
    }
    @Configuration @EnableTransactionManagement @EnableJpaRepositories(basePackageClasses=DocumentRepository.class)
    @Import(EvidenceReadinessService.class)
    static class ValidateLegacyConfig {
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("com.yu.transferrag.entity");
            f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate",
                "hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.PhysicalNamingStrategySnakeCaseImpl"));return f;
        }
        @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory f) {return new JpaTransactionManager(f);}
    }
}
