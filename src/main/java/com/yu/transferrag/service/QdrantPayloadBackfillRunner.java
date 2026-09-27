package com.yu.transferrag.service;

import com.yu.transferrag.dto.QdrantPayloadBackfillReport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Explicit opt-in maintenance entry point. Run it only with
 * spring.main.web-application-type=none; normal server startup never enables it.
 */
@Component
@ConditionalOnProperty(name = "app.qdrant-backfill.enabled", havingValue = "true")
public class QdrantPayloadBackfillRunner implements ApplicationRunner {

    private static final Logger logger = LoggerFactory.getLogger(QdrantPayloadBackfillRunner.class);

    private final QdrantPayloadBackfillService backfillService;
    private final ConfigurableApplicationContext applicationContext;
    private final boolean dryRun;

    public QdrantPayloadBackfillRunner(QdrantPayloadBackfillService backfillService,
                                       ConfigurableApplicationContext applicationContext,
                                       @Value("${app.qdrant-backfill.dry-run:true}") boolean dryRun) {
        this.backfillService = backfillService;
        this.applicationContext = applicationContext;
        this.dryRun = dryRun;
    }

    @Override
    public void run(ApplicationArguments args) {
        QdrantPayloadBackfillReport report = backfillService.backfill(dryRun);
        logger.info("Qdrant payload backfill complete: dryRun={}, scanned={}, requiresUpdate={}, "
                        + "updated={}, alreadyCorrect={}, missingChunks={}, canonicalPreserved={}, errors={}",
                report.dryRun(), report.scannedPoints(), report.requiresUpdate(), report.updatedPoints(),
                report.alreadyCorrect(), report.missingChunks(), report.canonicalPreserved(), report.errors());
        int exitCode = SpringApplication.exit(applicationContext, () -> report.errors() == 0 ? 0 : 1);
        System.exit(exitCode);
    }
}
