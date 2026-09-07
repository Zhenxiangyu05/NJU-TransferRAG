package com.yu.transferrag.util;

import java.util.ArrayList;
import java.util.List;

public class TextChunker {

    public List<String> split(String text, int chunkSize, int overlap) {
        if (text == null) {
            throw new IllegalArgumentException("text 不能为 null");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize 必须大于 0");
        }
        if (overlap < 0) {
            throw new IllegalArgumentException("overlap 必须大于或等于 0");
        }
        if (overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap 必须小于 chunkSize");
        }

        List<String> chunks = new ArrayList<>();
        int start = 0;

        while (start < text.length()) {
            int end = Math.min(start + chunkSize, text.length());
            String chunk = text.substring(start, end).trim();
            if (!chunk.isEmpty()) {
                chunks.add(chunk);
            }

            if (end == text.length()) {
                break;
            }
            start = end - overlap;
        }

        return chunks;
    }
}
