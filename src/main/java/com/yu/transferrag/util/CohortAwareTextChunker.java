package com.yu.transferrag.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class CohortAwareTextChunker {

    private static final Pattern COHORT_PATTERN = Pattern.compile(
            "(?<!\\d)((?:19|20)\\d{2})\\s*级"
    );
    private static final Pattern TITLE_SCOPE_PATTERN = Pattern.compile(
            "(?m)^\\s*(?<year>(?:19|20)\\d{2})\\s*级[^\\r\\n]{0,60}(?:指南|指北)"
    );
    private static final Pattern EXPLICIT_SCOPE_PATTERN = Pattern.compile(
            "适用范围[^。\\r\\n]{0,80}?(?<year>(?:19|20)\\d{2})\\s*级"
    );
    private static final Pattern TOP_LEVEL_HEADING_PATTERN = Pattern.compile(
            "^(?:#{1,2}\\s+|§\\s*\\d+|第[一二三四五六七八九十]+[章节部分]"
                    + "|[一二三四五六七八九十]+[、.．]\\s*|\\d+[.、]\\s*).*$"
    );

    private final TextChunker textChunker = new TextChunker();

    public List<CohortChunk> split(String text, int chunkSize, int overlap) {
        validate(text, chunkSize, overlap);
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');
        Integer defaultCohort = findDocumentScopeCohort(normalized);
        Integer currentCohort = defaultCohort;
        List<Section> sections = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String line : normalized.split("\\n", -1)) {
            String trimmed = line.trim();
            Integer lineCohort = uniqueCohortYear(line);
            boolean heading = TOP_LEVEL_HEADING_PATTERN.matcher(trimmed).matches();
            boolean cohortBoundary = lineCohort != null
                    && !Objects.equals(lineCohort, currentCohort);
            if (!current.isEmpty() && (heading || cohortBoundary)) {
                addSection(sections, current, currentCohort);
                current = new StringBuilder();
            }
            if (heading) {
                currentCohort = defaultCohort;
            }
            if (lineCohort != null) {
                currentCohort = lineCohort;
            }
            if (!current.isEmpty()) {
                current.append('\n');
            }
            current.append(line);
        }
        addSection(sections, current, currentCohort);

        List<CohortChunk> chunks = new ArrayList<>();
        for (Section section : sections) {
            for (String content : textChunker.split(section.content(), chunkSize, overlap)) {
                chunks.add(new CohortChunk(content, section.cohortYear()));
            }
        }
        return List.copyOf(chunks);
    }

    private Integer findDocumentScopeCohort(String text) {
        Matcher titleMatcher = TITLE_SCOPE_PATTERN.matcher(text);
        if (titleMatcher.find()) {
            return Integer.valueOf(titleMatcher.group("year"));
        }
        Matcher scopeMatcher = EXPLICIT_SCOPE_PATTERN.matcher(text);
        if (scopeMatcher.find() && scopeMatcher.start() < 800) {
            return Integer.valueOf(scopeMatcher.group("year"));
        }
        return null;
    }

    private Integer uniqueCohortYear(String line) {
        Matcher matcher = COHORT_PATTERN.matcher(line);
        Integer resolved = null;
        while (matcher.find()) {
            Integer year = Integer.valueOf(matcher.group(1));
            if (resolved != null && !resolved.equals(year)) {
                return null;
            }
            resolved = year;
        }
        return resolved;
    }

    private void addSection(List<Section> sections, StringBuilder content, Integer cohortYear) {
        String value = content.toString().trim();
        if (!value.isBlank()) {
            sections.add(new Section(value, cohortYear));
        }
    }

    private void validate(String text, int chunkSize, int overlap) {
        if (text == null) {
            throw new IllegalArgumentException("text 不能为 null");
        }
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize 必须大于 0");
        }
        if (overlap < 0 || overlap >= chunkSize) {
            throw new IllegalArgumentException("overlap 必须大于或等于 0 且小于 chunkSize");
        }
    }

    public record CohortChunk(String content, Integer cohortYear) {
    }

    private record Section(String content, Integer cohortYear) {
    }
}
