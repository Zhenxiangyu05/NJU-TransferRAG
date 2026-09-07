package com.yu.transferrag.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class StructuredPolicyChunker {

    private static final int MIN_RECORD_COUNT = 2;

    private static final Pattern RECORD_HEADER_PATTERN = Pattern.compile(
            "(?m)^[\\t ]*(?:(?<department>[\\p{IsHan}A-Za-z·（）()]{1,30}(?:学院|学系|系))[\\t ]+)?"
                    + "(?<year>(?:19|20)\\d{2})[\\t ]+"
                    + "(?<major>[^\\r\\n]+?)[\\t ]+"
                    + "(?<quota>\\d+)[\\t ]*$"
    );

    private static final Pattern DEPARTMENT_NAME_PATTERN = Pattern.compile(
            "^[\\p{IsHan}A-Za-z·（）()]{1,30}(?:学院|学系|系)$"
    );

    private static final Pattern ADDITIONAL_YEAR_ROW_PATTERN = Pattern.compile(
            "(?m)^[\\t ]*(?:19|20)\\d{2}(?:[\\t ]|$)"
    );

    private final TextChunker textChunker = new TextChunker();

    public List<PolicyChunk> split(String text, int chunkSize, int overlap) {
        validate(text, chunkSize, overlap);

        String normalizedText = text.replace("\r\n", "\n").replace('\r', '\n');
        List<RecordHeader> headers = findRecordHeaders(normalizedText);
        if (headers.size() < MIN_RECORD_COUNT) {
            return List.of();
        }

        Set<String> knownDepartments = findKnownDepartments(normalizedText, headers);
        List<PolicyChunk> chunks = new ArrayList<>();

        for (int index = 0; index < headers.size(); index++) {
            RecordHeader header = headers.get(index);
            int end = index + 1 < headers.size()
                    ? headers.get(index + 1).start()
                    : normalizedText.length();
            String recordContent = cleanRecord(normalizedText.substring(header.start(), end));
            if (recordContent.isBlank()) {
                continue;
            }

            String department = resolveDepartment(
                    header.department(),
                    header.major(),
                    knownDepartments
            );
            boolean reliableMetadata = hasReliableMetadata(
                    recordContent,
                    department,
                    knownDepartments
            );
            Integer policyYear = reliableMetadata ? header.policyYear() : null;
            String chunkDepartment = reliableMetadata ? department : null;
            String major = reliableMetadata ? header.major() : null;
            List<String> recordParts = textChunker.split(recordContent, chunkSize, overlap);
            for (int partIndex = 0; partIndex < recordParts.size(); partIndex++) {
                String content = recordParts.get(partIndex);
                if (partIndex > 0) {
                    content = header.originalLine() + "\n" + content;
                }
                chunks.add(new PolicyChunk(
                        content,
                        policyYear,
                        chunkDepartment,
                        major
                ));
            }
        }

        return chunks;
    }

    private boolean hasReliableMetadata(String recordContent,
                                        String resolvedDepartment,
                                        Set<String> knownDepartments) {
        int firstLineEnd = recordContent.indexOf('\n');
        String contentAfterHeader = firstLineEnd < 0
                ? ""
                : recordContent.substring(firstLineEnd + 1);
        if (ADDITIONAL_YEAR_ROW_PATTERN.matcher(contentAfterHeader).find()) {
            return false;
        }

        for (String line : contentAfterHeader.split("\\n", -1)) {
            String normalizedLine = normalize(line);
            if (knownDepartments.contains(normalizedLine)
                    && !normalizedLine.equals(resolvedDepartment)) {
                return false;
            }
        }
        return true;
    }

    private List<RecordHeader> findRecordHeaders(String text) {
        List<RecordHeader> headers = new ArrayList<>();
        Matcher matcher = RECORD_HEADER_PATTERN.matcher(text);
        while (matcher.find()) {
            String major = normalize(matcher.group("major"));
            if (!isPlausibleMajor(major)) {
                continue;
            }
            headers.add(new RecordHeader(
                    matcher.start(),
                    Integer.valueOf(matcher.group("year")),
                    normalize(matcher.group("department")),
                    major,
                    matcher.group().trim()
            ));
        }
        return headers;
    }

    private Set<String> findKnownDepartments(String text, List<RecordHeader> headers) {
        Set<String> departments = new LinkedHashSet<>();
        headers.stream()
                .map(RecordHeader::department)
                .filter(value -> value != null)
                .forEach(departments::add);

        String[] lines = text.split("\\n", -1);
        for (int index = 0; index < lines.length; index++) {
            String line = normalize(lines[index]);
            if (isDepartmentName(line)) {
                departments.add(line);
            }

            if (index + 1 < lines.length) {
                String nextLine = normalize(lines[index + 1]);
                if (isShortChineseFragment(line) && isShortChineseFragment(nextLine)) {
                    String combined = line + nextLine;
                    if (isDepartmentName(combined)) {
                        departments.add(combined);
                    }
                }
            }
        }
        return departments;
    }

    private String resolveDepartment(String explicitDepartment,
                                     String major,
                                     Set<String> knownDepartments) {
        if (explicitDepartment != null) {
            return explicitDepartment;
        }

        String normalizedMajor = compact(major)
                .replaceFirst("[（(].*$", "")
                .replaceFirst("类$", "");
        List<String> majorForms = new ArrayList<>();
        majorForms.add(normalizedMajor);
        if (normalizedMajor.endsWith("学") && normalizedMajor.length() > 2) {
            majorForms.add(normalizedMajor.substring(0, normalizedMajor.length() - 1));
        }

        String bestDepartment = null;
        int bestScore = 0;
        boolean tied = false;
        for (String department : knownDepartments) {
            int score = departmentScore(department, majorForms);
            if (score > bestScore) {
                bestDepartment = department;
                bestScore = score;
                tied = false;
            } else if (score > 0 && score == bestScore) {
                tied = true;
            }
        }
        return bestScore == 0 || tied ? null : bestDepartment;
    }

    private int departmentScore(String department, List<String> majorForms) {
        String normalizedDepartment = compact(department);
        String departmentCore = normalizedDepartment.replaceFirst("(?:学院|学系|系)$", "");
        int bestScore = 0;

        for (String majorForm : majorForms) {
            if (majorForm.length() >= 2 && normalizedDepartment.contains(majorForm)) {
                bestScore = Math.max(bestScore, 200 + majorForm.length());
            }
            if (departmentCore.length() >= 2 && majorForm.contains(departmentCore)) {
                bestScore = Math.max(bestScore, 150 + departmentCore.length());
            }
        }
        return bestScore;
    }

    private String cleanRecord(String record) {
        StringBuilder cleaned = new StringBuilder(record.length());
        for (String line : record.split("\\n", -1)) {
            String normalizedLine = line.trim();
            if (isBoilerplate(normalizedLine)) {
                continue;
            }
            if (!normalizedLine.isEmpty()) {
                if (!cleaned.isEmpty()) {
                    cleaned.append('\n');
                }
                cleaned.append(normalizedLine);
            }
        }
        return cleaned.toString();
    }

    private boolean isBoilerplate(String line) {
        return line.equals("学院名称 年级 专业名称")
                || line.equals("接收计划")
                || line.equals("名额")
                || line.startsWith("准入标准 准入审核依据及实施方法")
                || line.matches("第 \\d+ 页，共 \\d+ 页.*")
                || (line.startsWith("南京大学") && line.contains("准入计划"));
    }

    private boolean isPlausibleMajor(String major) {
        return major != null
                && major.length() <= 60
                && major.matches(".*[\\p{IsHan}A-Za-z].*");
    }

    private boolean isDepartmentName(String value) {
        return value != null && DEPARTMENT_NAME_PATTERN.matcher(value).matches();
    }

    private boolean isShortChineseFragment(String value) {
        return value != null
                && value.length() <= 12
                && value.matches("[\\p{IsHan}A-Za-z·（）()]+?");
    }

    private String compact(String value) {
        return value == null ? "" : value.replaceAll("\\s+", "");
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().replaceAll("[\\t ]+", " ");
    }

    private void validate(String text, int chunkSize, int overlap) {
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
    }

    public record PolicyChunk(
            String content,
            Integer policyYear,
            String department,
            String major
    ) {
    }

    private record RecordHeader(
            int start,
            Integer policyYear,
            String department,
            String major,
            String originalLine
    ) {
    }
}
