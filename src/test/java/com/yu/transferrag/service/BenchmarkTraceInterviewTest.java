package com.yu.transferrag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yu.transferrag.dto.RagResponse;
import com.yu.transferrag.exception.AiServiceUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/** Opt-in isolated V1 or V2 run over an immutable, Gold-confirmed subset. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "benchmark.trace.enabled", matches = "true")
class BenchmarkTraceInterviewTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private RagService ragService;
    @Autowired private Environment environment;

    @Test
    void captureFrozenSubsetForOneVersion() throws Exception {
        if (!"validate".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto"))) {
            throw new IllegalStateException("Read-only benchmark requires spring.jpa.hibernate.ddl-auto=validate");
        }
        if (environment.getProperty("app.qdrant-backfill.enabled", Boolean.class, false)) {
            throw new IllegalStateException("Qdrant payload backfill must be disabled during benchmark");
        }
        if (!"never".equals(environment.getProperty("spring.sql.init.mode"))) {
            throw new IllegalStateException("SQL initialization must be disabled during benchmark");
        }
        if (environment.getProperty("spring.ai.vectorstore.qdrant.initialize-schema", Boolean.class, true)) {
            throw new IllegalStateException("Qdrant schema initialization must be disabled during benchmark");
        }

        String version = environment.getProperty("benchmark.trace.version", "").trim().toUpperCase();
        if (!List.of("V1", "V2").contains(version)) {
            throw new IllegalArgumentException("benchmark.trace.version must be V1 or V2");
        }
        Path root = Path.of("").toAbsolutePath();
        Path subsetPath = Path.of(environment.getProperty("benchmark.trace.subset",
                "evaluation/benchmark/interview-benchmark-24.json"));
        JsonNode subset = JSON.readTree(root.resolve(subsetPath).toFile());
        JsonNode subsetCases = subset.path("cases");
        boolean fullBenchmark = subset.has("fullBenchmarkCaseCount");
        int expectedCount = fullBenchmark ? subset.path("fullBenchmarkCaseCount").asInt() : 24;
        assertEquals(expectedCount, subsetCases.size(), "Frozen subset size mismatch");
        if (fullBenchmark && expectedCount <= 24) {
            throw new IllegalStateException("Full benchmark requires more than 24 cases");
        }
        if (fullBenchmark) {
            JsonNode freeze = JSON.readTree(root.resolve("evaluation/benchmark/full-gold-freeze.json").toFile());
            assertEquals(sha256(root.resolve("evaluation/test-cases.json")),
                    freeze.path("testCasesHash").asText(), "Frozen test cases changed");
            assertEquals(sha256(root.resolve("evaluation/benchmark/gold-labels.json")),
                    freeze.path("goldLabelsHash").asText(), "Gold changed after freeze");
            assertEquals(expectedCount, freeze.path("confirmed").asInt(), "Gold/subset count mismatch");
            assertEquals(subset.path("productionLogicVersion").asText(),
                    freeze.path("productionLogicVersion").asText(), "Production logic version mismatch");
        }

        Map<String, JsonNode> sourceCases = new LinkedHashMap<>();
        for (JsonNode row : JSON.readTree(root.resolve("evaluation/test-cases.json").toFile())) {
            sourceCases.put(row.path("caseId").asText(), row);
        }
        Map<String, JsonNode> goldCases = new LinkedHashMap<>();
        for (JsonNode row : JSON.readTree(root.resolve("evaluation/benchmark/gold-labels.json").toFile())
                .path("cases")) {
            goldCases.put(row.path("caseId").asText(), row);
        }
        if (fullBenchmark) {
            List<String> confirmedIds = new ArrayList<>();
            for (String caseId : sourceCases.keySet()) {
                JsonNode gold = goldCases.get(caseId);
                if (gold != null && "CONFIRMED".equals(gold.path("reviewStatus").asText())) {
                    confirmedIds.add(caseId);
                }
            }
            List<String> selectedIds = new ArrayList<>();
            for (JsonNode selected : subsetCases) selectedIds.add(selected.path("caseId").asText());
            assertEquals(confirmedIds, selectedIds, "Full subset must contain every confirmed case in frozen order");
        }

        Path resultRoot = root.resolve("evaluation/results").toAbsolutePath().normalize();
        String configuredOutput = environment.getProperty("benchmark.trace.output", "");
        Path output = Path.of(configuredOutput).toAbsolutePath().normalize();
        if (configuredOutput.isBlank() || !output.startsWith(resultRoot)) {
            throw new IllegalArgumentException("Benchmark output must be a new directory under evaluation/results");
        }
        Files.createDirectories(output);
        Path rawOutput = output.resolve("raw-" + version.toLowerCase() + ".jsonl");
        Path progressOutput = output.resolve("progress-" + version.toLowerCase() + ".log");
        if (Files.exists(rawOutput) || Files.exists(progressOutput)) {
            throw new IllegalArgumentException("Refusing to overwrite existing benchmark trace/progress");
        }
        Files.writeString(rawOutput, "", StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
        Files.writeString(progressOutput, "STARTED " + version + System.lineSeparator(),
                StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);

        List<Map<String, Object>> rows = new ArrayList<>();
        for (JsonNode selected : subsetCases) {
            String caseId = selected.path("caseId").asText();
            JsonNode source = sourceCases.get(caseId);
            JsonNode gold = goldCases.get(caseId);
            assertNotNull(source, "Missing frozen test case: " + caseId);
            assertNotNull(gold, "Missing Gold row: " + caseId);
            if (!"CONFIRMED".equals(gold.path("reviewStatus").asText())) {
                throw new IllegalStateException("Subset contains unconfirmed Gold: " + caseId);
            }
            String question = source.path("question").asText();
            if (question.isBlank()) throw new IllegalStateException("Missing question for " + caseId);

            long started = System.nanoTime();
            RagService.EvaluationTraceResult result = null;
            String errorClass = null;
            String firstFailureClass = null;
            String providerStage = null;
            Integer upstreamStatus = null;
            int infraRetry = 0;
            try {
                result = ragService.askForEvaluation(question, version.equals("V2"));
            } catch (Exception exception) {
                firstFailureClass = exception.getClass().getName();
                AiServiceUnavailableException provider = providerFailure(exception);
                if (provider != null) {
                    providerStage = provider.getStage().name();
                    upstreamStatus = provider.getUpstreamStatus();
                }
                if (provider != null && (upstreamStatus == null || upstreamStatus >= 500)) {
                    infraRetry = 1;
                    try {
                        result = ragService.askForEvaluation(question, version.equals("V2"));
                    } catch (Exception retryFailure) {
                        errorClass = retryFailure.getClass().getName();
                        AiServiceUnavailableException retryProvider = providerFailure(retryFailure);
                        if (retryProvider != null) {
                            providerStage = retryProvider.getStage().name();
                            upstreamStatus = retryProvider.getUpstreamStatus();
                        }
                    }
                } else {
                    errorClass = firstFailureClass;
                }
            }
            long elapsedMs = (System.nanoTime() - started) / 1_000_000;
            Map<String, Object> traceRow = row(caseId, version, question, source, gold, result, elapsedMs,
                    errorClass, firstFailureClass, providerStage, upstreamStatus, infraRetry);
            rows.add(traceRow);
            Files.writeString(rawOutput, JSON.writeValueAsString(traceRow) + "\n",
                    StandardOpenOption.APPEND);
            Files.writeString(progressOutput, caseId + " "
                    + (errorClass == null ? "COMPLETED" : "FAILED_" + errorClass) + System.lineSeparator(),
                    StandardOpenOption.APPEND);
            System.out.printf("Completed %s %d/%d: %s (%s)%n", version, rows.size(), expectedCount, caseId,
                    errorClass == null ? "response" : "infrastructure-error");
        }

        JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("trace-metadata-"
                + version.toLowerCase() + ".json").toFile(), Map.of(
                "caseCount", rows.size(), "version", version, "createdAtUtc", Instant.now().toString(),
                "subsetVersion", fullBenchmark ? subset.path("datasetVersion").asText()
                                                : subset.path("subsetVersion").asText(),
                "productionLogicVersion", subset.path("productionLogicVersion").asText(),
                "benchmarkToolingVersion", subset.path("benchmarkToolingVersion").asText(),
                "mode", "in-process RagService evaluation trace", "productionApiChanged", false));
        System.out.println("Interview benchmark trace saved for " + version + ": " + rawOutput);
        assertEquals(expectedCount, rows.size());
    }

    private static String sha256(Path path) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

    private static AiServiceUnavailableException providerFailure(Throwable failure) {
        for (Throwable current = failure; current != null; current = current.getCause()) {
            if (current instanceof AiServiceUnavailableException provider) return provider;
        }
        return null;
    }

    private Map<String, Object> row(String caseId, String version, String question, JsonNode source,
                                    JsonNode gold, RagService.EvaluationTraceResult result, long elapsedMs,
                                    String errorClass, String firstFailureClass, String providerStage,
                                    Integer upstreamStatus, int infraRetry) {
        Map<String, Object> row = new LinkedHashMap<>();
        RagResponse response = result == null ? null : result.response();
        EvaluationTrace trace = result == null ? null : result.trace();
        row.put("caseId", caseId);
        row.put("version", version);
        row.put("query", question);
        row.put("category", source.path("category").asText(null));
        row.put("answer", response == null ? null : response.getAnswer());
        row.put("executionStatus", errorClass == null ? "COMPLETED" : "FAILED");
        row.put("latencyMs", elapsedMs);
        row.put("finalAttemptLatencyMs", trace == null ? null : trace.latencyMs());
        row.put("retrievalLayer", trace == null ? null : trace.retrievalLayer());
        row.put("fallback", trace == null ? null : trace.fallback());
        row.put("fallbackReason", trace == null ? null : trace.fallbackReason());
        row.put("rawRetrievedCandidates", trace == null ? List.of() : trace.rawRetrievedCandidates());
        row.put("canonicalRanking", trace == null ? List.of() : trace.canonicalRanking());
        row.put("evidenceEquivalentRanking", trace == null ? List.of() : trace.evidenceEquivalentRanking());
        row.put("evidenceFallbackRanking", trace == null ? List.of() : trace.evidenceFallbackRanking());
        row.put("retrievedContexts", trace == null ? List.of() : trace.retrievedContexts());
        row.put("approvedContexts", trace == null ? List.of() : trace.approvedContexts());
        List<Long> citations = trace == null || trace.finalCitationDocumentIds() == null
                ? List.of() : trace.finalCitationDocumentIds();
        row.put("finalCitationDocumentIds", citations);
        row.put("citationDocumentIds", citations);
        JsonNode resolvedFacts = gold.has("expectedFactsOverride")
                ? gold.path("expectedFactsOverride") : source.path("expectedFacts");
        row.put("expectedFacts", JSON.convertValue(resolvedFacts, List.class));
        row.put("expectedDocumentIds", gold.path("expectedDocumentIds"));
        row.put("answerableGold", gold.path("answerable").asBoolean());
        row.put("reviewStatus", gold.path("reviewStatus").asText());
        row.put("reviewSource", gold.path("reviewSource").asText());
        row.put("verifiedEvidence", JSON.convertValue(gold.path("verifiedEvidence"), List.class));
        row.put("department", source.path("department").asText(null));
        row.put("major", source.path("major").asText(null));
        row.put("sourceYear", source.path("sourceYear").isNumber()
                ? source.path("sourceYear").asInt() : null);
        row.put("referenceAnswer", gold.path("referenceAnswer").isTextual()
                ? gold.path("referenceAnswer").asText() : null);
        row.put("contextAvailability", "CAPTURED_FROM_LIVE_SERVICE");
        row.put("error", errorClass);
        row.put("infraRetry", infraRetry);
        row.put("firstFailureClass", firstFailureClass);
        row.put("providerStage", providerStage);
        row.put("upstreamStatus", upstreamStatus);
        return row;
    }

}
