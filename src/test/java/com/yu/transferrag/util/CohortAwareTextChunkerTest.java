package com.yu.transferrag.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CohortAwareTextChunkerTest {

    private final CohortAwareTextChunker chunker = new CohortAwareTextChunker();

    @Test
    void shouldExtractExplicitCohortYear() {
        List<CohortAwareTextChunker.CohortChunk> chunks =
                chunker.split("2023级学生分流情况：新传57人。", 1000, 150);

        assertEquals(1, chunks.size());
        assertEquals(2023, chunks.getFirst().cohortYear());
    }

    @Test
    void shouldNotTreatOrdinaryCalendarYearAsCohort() {
        List<CohortAwareTextChunker.CohortChunk> chunks =
                chunker.split("2023年发布本指南。", 1000, 150);

        assertNull(chunks.getFirst().cohortYear());
    }

    @Test
    void shouldSeparateDifferentCohorts() {
        String text = """
                2023级情况
                2023级事实。
                2024级情况
                2024级事实。
                """;

        List<CohortAwareTextChunker.CohortChunk> chunks = chunker.split(text, 1000, 150);

        assertEquals(List.of(2023, 2024),
                chunks.stream().map(CohortAwareTextChunker.CohortChunk::cohortYear).toList());
        assertFalse(chunks.getFirst().content().contains("2024级"));
    }

    @Test
    void shouldStopLocalInheritanceAtTopLevelHeading() {
        String text = """
                前言
                2023级分流情况。
                2. 学习建议
                普通学习建议。
                """;

        List<CohortAwareTextChunker.CohortChunk> chunks = chunker.split(text, 1000, 150);

        assertEquals(3, chunks.size());
        assertNull(chunks.getFirst().cohortYear());
        assertEquals(2023, chunks.get(1).cohortYear());
        assertNull(chunks.get(2).cohortYear());
    }

    @Test
    void shouldUseExplicitDocumentScopeAsConservativeDefault() {
        String text = """
                适用范围：以 2025 级经验为主，仅供参考。
                一、分流方向
                智能科学与技术、自动化、集成电路、数字经济。
                """;

        List<CohortAwareTextChunker.CohortChunk> chunks = chunker.split(text, 1000, 150);

        assertTrue(chunks.stream().allMatch(chunk -> chunk.cohortYear() == 2025));
    }
}
