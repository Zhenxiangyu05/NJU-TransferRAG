package com.yu.transferrag.service;

import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class PlainTextParserService {

    public String extractText(Path filePath) {
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new IllegalStateException("文本文件不存在或不是普通文件");
        }

        try {
            String text = Files.readString(filePath, StandardCharsets.UTF_8);
            if (text.isBlank()) {
                throw new IllegalStateException("文本文件内容为空");
            }
            return text;
        } catch (IOException e) {
            throw new IllegalStateException("文本文件无法按 UTF-8 读取", e);
        }
    }
}
