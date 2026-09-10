package com.yu.transferrag.dto;

import java.util.List;

public record QueryRewriteResult(
        String originalQuery,
        String rewrittenQuery,
        List<MatchedEntity> matchedEntities,
        List<ResolvedEntity> resolvedEntities,
        List<String> departments,
        List<String> majors,
        List<String> ambiguousEntities,
        Integer explicitYear,
        Integer resolvedYear,
        boolean multiYearQuery
) {

    public QueryRewriteResult {
        matchedEntities = List.copyOf(matchedEntities);
        resolvedEntities = List.copyOf(resolvedEntities);
        departments = List.copyOf(departments);
        majors = List.copyOf(majors);
        ambiguousEntities = List.copyOf(ambiguousEntities);
    }

    public QueryRewriteResult(String originalQuery,
                              String rewrittenQuery,
                              List<MatchedEntity> matchedEntities) {
        this(originalQuery, rewrittenQuery, matchedEntities,
                List.of(), legacyDepartments(matchedEntities), legacyMajors(matchedEntities), List.of(),
                null, null, false);
    }

    public QueryRewriteResult(String originalQuery,
                              String rewrittenQuery,
                              List<MatchedEntity> matchedEntities,
                              Integer explicitYear,
                              Integer resolvedYear,
                              boolean multiYearQuery) {
        this(
                originalQuery,
                rewrittenQuery,
                matchedEntities,
                List.of(),
                legacyDepartments(matchedEntities),
                legacyMajors(matchedEntities),
                List.of(),
                explicitYear,
                resolvedYear,
                multiYearQuery
        );
    }


    public QueryRewriteResult withResolvedYear(Integer year) {
        return new QueryRewriteResult(
                originalQuery,
                rewrittenQuery,
                matchedEntities,
                resolvedEntities,
                departments,
                majors,
                ambiguousEntities,
                explicitYear,
                year,
                multiYearQuery
        );
    }

    private static List<String> legacyDepartments(List<MatchedEntity> entities) {
        return entities.stream()
                .map(MatchedEntity::department)
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static List<String> legacyMajors(List<MatchedEntity> entities) {
        return entities.stream()
                .filter(entity -> "MAJOR".equalsIgnoreCase(entity.entityType())
                        || "PROGRAM".equalsIgnoreCase(entity.entityType()))
                .map(MatchedEntity::standardName)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }
}
