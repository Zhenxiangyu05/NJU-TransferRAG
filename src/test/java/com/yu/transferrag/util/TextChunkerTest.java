package com.yu.transferrag.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TextChunkerTest {

    private final TextChunker textChunker = new TextChunker();

    @Test
    void shouldSplitTextWithOverlap() {
        List<String> chunks = textChunker.split("ABCDEFGHIJKLMN", 6, 2);

        assertEquals(List.of("ABCDEF", "EFGHIJ", "IJKLMN"), chunks);
    }

    @Test
    void shouldReturnOneChunkWhenTextIsShorterThanChunkSize() {
        List<String> chunks = textChunker.split("ABC", 6, 2);

        assertEquals(List.of("ABC"), chunks);
    }

    @Test
    void shouldSplitContinuouslyWhenOverlapIsZero() {
        List<String> chunks = textChunker.split("ABCDEFGHIJKLMN", 6, 0);

        assertEquals(List.of("ABCDEF", "GHIJKL", "MN"), chunks);
    }

    @Test
    void shouldThrowExceptionWhenOverlapIsGreaterThanOrEqualToChunkSize() {
        assertThrows(IllegalArgumentException.class,
                () -> textChunker.split("ABC", 6, 6));
    }

    @Test
    void shouldThrowExceptionWhenTextIsNull() {
        assertThrows(IllegalArgumentException.class,
                () -> textChunker.split(null, 6, 2));
    }
}
