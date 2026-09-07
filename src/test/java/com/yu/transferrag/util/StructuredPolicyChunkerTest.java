package com.yu.transferrag.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StructuredPolicyChunkerTest {

    private final StructuredPolicyChunker chunker = new StructuredPolicyChunker();

    @Test
    void shouldSplitGlobalPolicyTextByProfessionalRecord() {
        String text = """
                学院名称 年级 专业名称
                文学院 2025 汉语言文学 11
                汉语言文学申请条件和考核方式。
                法学院 2025 法学 15
                法学申请条件和考核方式。
                """;

        List<StructuredPolicyChunker.PolicyChunk> chunks = chunker.split(text, 1000, 150);

        assertEquals(2, chunks.size());
        assertEquals(2025, chunks.get(0).policyYear());
        assertEquals("文学院", chunks.get(0).department());
        assertEquals("汉语言文学", chunks.get(0).major());
        assertEquals(2025, chunks.get(1).policyYear());
        assertEquals("法学院", chunks.get(1).department());
        assertEquals("法学", chunks.get(1).major());
    }

    @Test
    void shouldInferUniqueDepartmentFromMajorAndKnownDepartmentNames() {
        String text = """
                2025 法学 17
                法学2025年申请条件。
                2024 法学 4
                法学2024年申请条件。
                法学院
                """;

        List<StructuredPolicyChunker.PolicyChunk> chunks = chunker.split(text, 1000, 150);

        assertEquals(2, chunks.size());
        assertEquals("法学院", chunks.get(0).department());
        assertEquals("法学院", chunks.get(1).department());
    }

    @Test
    void shouldKeepMetadataWhenLongProfessionalRecordIsSplitAgain() {
        String longRequirement = "申请条件。".repeat(500);
        String text = """
                文学院 2025 汉语言文学 11
                %s
                法学院 2025 法学 15
                法学申请条件。
                """.formatted(longRequirement);

        List<StructuredPolicyChunker.PolicyChunk> chunks = chunker.split(text, 1000, 150);
        List<StructuredPolicyChunker.PolicyChunk> chineseLiteratureChunks = chunks.stream()
                .filter(chunk -> "汉语言文学".equals(chunk.major()))
                .toList();

        assertTrue(chineseLiteratureChunks.size() > 1);
        assertTrue(chineseLiteratureChunks.stream().allMatch(chunk ->
                chunk.policyYear() == 2025
                        && "文学院".equals(chunk.department())
                        && "汉语言文学".equals(chunk.major())
        ));
    }

    @Test
    void shouldReturnEmptyWhenRecordStructureIsNotReliable() {
        String text = "这是一篇普通政策说明，只在正文中提到了2025年，但没有专业表格记录。";

        assertTrue(chunker.split(text, 1000, 150).isEmpty());
    }

    @Test
    void shouldLeaveMetadataEmptyWhenOneSegmentContainsAnotherUnparsedYearRow() {
        String text = """
                2025 新闻传播学类 15
                新闻传播学类申请条件。
                社会学院
                2024 4
                另一条无法完整识别的记录。
                法学院 2025 法学 15
                法学申请条件。
                """;

        List<StructuredPolicyChunker.PolicyChunk> chunks = chunker.split(text, 1000, 150);

        assertEquals(2, chunks.size());
        assertEquals(null, chunks.get(0).policyYear());
        assertEquals(null, chunks.get(0).department());
        assertEquals(null, chunks.get(0).major());
        assertEquals(2025, chunks.get(1).policyYear());
        assertEquals("法学院", chunks.get(1).department());
    }
}
