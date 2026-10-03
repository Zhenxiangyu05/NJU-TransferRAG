package com.yu.transferrag.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yu.transferrag.dto.RagResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Opt-in local runner. It calls the real services in-process and is disabled in ordinary test runs. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@EnabledIfSystemProperty(named = "benchmark.trace.enabled", matches = "true")
class BenchmarkTraceSmokeTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Autowired private RagService ragService;
    @Autowired private Environment environment;

    @Test
    void captureExistingSixCasesForV1AndV2() throws Exception {
        if (!"validate".equals(environment.getProperty("spring.jpa.hibernate.ddl-auto"))) {
            throw new IllegalStateException("Read-only trace run requires spring.jpa.hibernate.ddl-auto=validate");
        }
        if (environment.getProperty("app.qdrant-backfill.enabled", Boolean.class, false)) {
            throw new IllegalStateException("Qdrant payload backfill must be disabled for benchmark tracing");
        }
        Path root = Path.of("").toAbsolutePath();
        Map<String, JsonNode> sourceCases = new LinkedHashMap<>();
        for (String file : List.of("evaluation/test-cases.json", "evaluation/smoke-cases.json")) {
            Path path = root.resolve(file);
            if (Files.exists(path)) {
                for (JsonNode row : JSON.readTree(path.toFile())) {
                    sourceCases.put(row.path("caseId").asText(), row);
                }
            }
        }
        JsonNode gold = JSON.readTree(root.resolve("evaluation/benchmark/smoke-gold.json").toFile());
        List<Map<String, Object>> v1 = new ArrayList<>();
        List<Map<String, Object>> v2 = new ArrayList<>();
        for (JsonNode goldCase : gold.path("cases")) {
            String caseId = goldCase.path("caseId").asText();
            JsonNode source = sourceCases.get(caseId);
            String question = goldCase.path("question").asText(source == null ? "" : source.path("question").asText());
            if (question.isBlank()) throw new IllegalStateException("Missing smoke query for " + caseId);
            for (String version : List.of("V1", "V2")) {
                RagService.EvaluationTraceResult result = ragService.askForEvaluation(question, version.equals("V2"));
                Map<String, Object> row = row(caseId, version, question, source, goldCase,
                        result.response(), result.trace());
                (version.equals("V1") ? v1 : v2).add(row);
            }
        }
        String configuredOutput = System.getProperty("benchmark.trace.output");
        String stamp = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssSSS'Z'").withZone(ZoneOffset.UTC)
                .format(Instant.now());
        Path resultRoot = root.resolve("evaluation/results").toAbsolutePath().normalize();
        Path output = configuredOutput == null || configuredOutput.isBlank()
                ? resultRoot.resolve("trace-" + stamp)
                : Path.of(configuredOutput).toAbsolutePath().normalize();
        if (!output.startsWith(resultRoot)) {
            throw new IllegalArgumentException("Benchmark trace output must stay under evaluation/results");
        }
        if (Files.exists(output)) throw new IllegalArgumentException("Benchmark trace output already exists");
        Files.createDirectories(output);
        writeJsonl(output.resolve("raw-v1.jsonl"), v1);
        writeJsonl(output.resolve("raw-v2.jsonl"), v2);
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.resolve("trace-metadata.json").toFile(), Map.of(
                "caseCount", gold.path("cases").size(), "requests", v1.size() + v2.size(),
                "createdAtUtc", stamp, "mode", "in-process RagService evaluation trace",
                "productionApiChanged", false));
        System.out.println("Benchmark trace saved: " + output.toAbsolutePath());
        assertEquals(6, v1.size());
        assertEquals(6, v2.size());
    }

    private Map<String, Object> row(String caseId, String version, String question,
                                    JsonNode source, JsonNode gold, RagResponse response,
                                    EvaluationTrace trace) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("caseId", caseId);
        row.put("version", version);
        row.put("query", question);
        row.put("answer", response.getAnswer());
        row.put("executionStatus", "COMPLETED");
        row.put("latencyMs", trace.latencyMs());
        row.put("retrievalLayer", trace.retrievalLayer());
        row.put("fallback", trace.fallback());
        row.put("fallbackReason", trace.fallbackReason());
        row.put("rawRetrievedCandidates", trace.rawRetrievedCandidates());
        row.put("canonicalRanking", trace.canonicalRanking());
        row.put("evidenceEquivalentRanking", trace.evidenceEquivalentRanking());
        row.put("evidenceFallbackRanking", trace.evidenceFallbackRanking());
        row.put("retrievedContexts", trace.retrievedContexts());
        row.put("approvedContexts", trace.approvedContexts());
        row.put("finalCitationDocumentIds", trace.finalCitationDocumentIds());
        row.put("citationDocumentIds", trace.finalCitationDocumentIds());
        row.put("expectedFacts", source == null || !source.path("expectedFacts").isArray() ? List.of()
                : JSON.convertValue(source.path("expectedFacts"),
                JSON.getTypeFactory().constructCollectionType(List.class, String.class)));
        row.put("expectedDocumentIds", nullableLongs(gold.path("expectedDocumentIds")));
        row.put("answerableGold", gold.path("answerable").isBoolean() ? gold.path("answerable").asBoolean() : null);
        row.put("manualReview", gold.path("manualReviewRequired").asBoolean(true));
        row.put("contextAvailability", "CAPTURED_FROM_LIVE_SERVICE");
        row.put("error", null);
        return row;
    }

    private List<Long> nullableLongs(JsonNode node) {
        if (!node.isArray()) return null;
        List<Long> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asLong()));
        return values;
    }

    private void writeJsonl(Path path, List<Map<String, Object>> rows) throws Exception {
        StringBuilder output = new StringBuilder();
        for (Map<String, Object> row : rows) output.append(JSON.writeValueAsString(row)).append('\n');
        Files.writeString(path, output);
    }
}
