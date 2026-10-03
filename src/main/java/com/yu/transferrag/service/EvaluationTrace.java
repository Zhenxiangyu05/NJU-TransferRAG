package com.yu.transferrag.service;

import com.yu.transferrag.dto.RagResponse;
import com.yu.transferrag.dto.SearchResultResponse;
import com.yu.transferrag.dto.SourceResponse;
import com.yu.transferrag.entity.DocumentRole;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/** Internal-only snapshot used by the local benchmark runner; never exposed by a controller. */
record EvaluationTrace(
        String query,
        String retrievalLayer,
        boolean fallback,
        String fallbackReason,
        List<Candidate> rawRetrievedCandidates,
        List<Candidate> canonicalRanking,
        List<Candidate> evidenceEquivalentRanking,
        List<Candidate> evidenceFallbackRanking,
        List<String> retrievedContexts,
        List<String> approvedContexts,
        List<Long> finalCitationDocumentIds,
        String answer,
        long latencyMs) {

    record Candidate(int rank, Long chunkId, Long documentId, Double score,
                     String documentRole, String section, String content) { }

    static final class Builder {
        private final String query;
        private String retrievalLayer;
        private boolean fallback;
        private String fallbackReason;
        private List<Candidate> rawRetrievedCandidates = List.of();
        private List<Candidate> canonicalRanking = List.of();
        private List<Candidate> evidenceEquivalentRanking = List.of();
        private List<Candidate> evidenceFallbackRanking = List.of();
        private final List<String> retrievedContexts = new ArrayList<>();
        private final List<String> approvedContexts = new ArrayList<>();

        Builder(String query) { this.query = query; }

        void evidenceRanking(List<SearchResultResponse> results, boolean fallbackSearch) {
            List<Candidate> candidates = candidates(results);
            rawRetrievedCandidates = candidates;
            retrievalLayer = "EVIDENCE";
            if (fallbackSearch) evidenceFallbackRanking = candidates;
        }

        void canonicalRanking(List<SearchResultResponse> results) {
            canonicalRanking = candidates(results);
            rawRetrievedCandidates = canonicalRanking;
            retrievalLayer = "CANONICAL";
        }

        void evidenceEquivalentRanking(List<Candidate> candidates) {
            evidenceEquivalentRanking = List.copyOf(candidates);
        }

        void fallback(String reason) {
            fallback = true;
            fallbackReason = reason;
        }

        void retrievedContext(String context) {
            if (context != null && !context.isBlank()) retrievedContexts.add(context);
        }

        void approvedContext(String context) {
            if (context != null && !context.isBlank()) approvedContexts.add(context);
        }

        EvaluationTrace finish(RagResponse response, long latencyMs) {
            List<Long> citationIds = response == null || response.getSources() == null ? List.of()
                    : response.getSources().stream().map(SourceResponse::getDocumentId)
                    .filter(id -> id != null).distinct().toList();
            return new EvaluationTrace(query, retrievalLayer, fallback,
                    fallbackReason == null ? "NONE" : fallbackReason,
                    rawRetrievedCandidates, canonicalRanking, evidenceEquivalentRanking,
                    evidenceFallbackRanking, List.copyOf(retrievedContexts),
                    List.copyOf(approvedContexts), citationIds,
                    response == null ? null : response.getAnswer(), latencyMs);
        }

        private List<Candidate> candidates(List<SearchResultResponse> results) {
            List<Candidate> candidates = new ArrayList<>();
            for (int index = 0; index < results.size(); index++) {
                SearchResultResponse result = results.get(index);
                candidates.add(new Candidate(index + 1, result.getChunkId(), result.getDocumentId(),
                        result.getScore(), result.getDocumentRole() == null ? null
                        : result.getDocumentRole().name(), result.getSection(), result.getContent()));
            }
            return List.copyOf(candidates);
        }

        static List<Candidate> projectEvidence(List<SearchResultResponse> canonicalResults,
                                               List<com.yu.transferrag.entity.EvidenceRef> refs) {
            LinkedHashSet<Long> seen = new LinkedHashSet<>();
            List<Candidate> projected = new ArrayList<>();
            for (SearchResultResponse canonical : canonicalResults) {
                refs.stream().filter(ref -> ref.getCanonicalChunk() != null
                                && canonical.getChunkId() != null
                                && canonical.getChunkId().equals(ref.getCanonicalChunk().getId()))
                        .filter(ref -> ref.getEvidenceDocument() != null
                                && ref.getEvidenceDocument().getDocumentRole() == DocumentRole.EVIDENCE)
                        .map(ref -> ref.getEvidenceDocument().getId())
                        .filter(id -> id != null && seen.add(id))
                        .forEach(id -> projected.add(new Candidate(projected.size() + 1,
                                canonical.getChunkId(), id, canonical.getScore(), "EVIDENCE",
                                canonical.getSection(), null)));
            }
            return List.copyOf(projected);
        }
    }
}
