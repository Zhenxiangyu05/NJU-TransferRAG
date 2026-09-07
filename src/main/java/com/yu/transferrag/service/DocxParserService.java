package com.yu.transferrag.service;

import org.apache.poi.ooxml.POIXMLException;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

@Service
public class DocxParserService {

    public String extractText(Path filePath) {
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new IllegalStateException("DOCX 文件不存在或不是普通文件");
        }

        String text;
        try (InputStream inputStream = Files.newInputStream(filePath);
             XWPFDocument document = new XWPFDocument(inputStream)) {
            text = extractBodyText(document);
        } catch (IOException | POIXMLException e) {
            throw new IllegalStateException("DOCX 文件无法读取", e);
        }

        if (text.isBlank()) {
            throw new IllegalStateException("DOCX 文件未提取到文本");
        }
        return text;
    }

    private String extractBodyText(XWPFDocument document) {
        StringBuilder text = new StringBuilder();

        for (IBodyElement bodyElement : document.getBodyElements()) {
            if (bodyElement instanceof XWPFParagraph paragraph) {
                appendLine(text, paragraph.getText());
            } else if (bodyElement instanceof XWPFTable table) {
                appendTable(text, table);
            }
        }
        return text.toString();
    }

    private void appendTable(StringBuilder text, XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            List<String> cellTexts = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                String cellText = cell.getText();
                cellTexts.add(cellText == null ? "" : cellText.strip());
            }
            appendLine(text, String.join("\t", cellTexts));
        }
    }

    private void appendLine(StringBuilder text, String line) {
        if (line == null || line.isBlank()) {
            return;
        }
        if (!text.isEmpty()) {
            text.append(System.lineSeparator());
        }
        text.append(line.strip());
    }
}
