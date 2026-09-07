package com.yu.transferrag.dto;

import java.util.List;

public record QueryRewriteResult(
        String originalQuery,
        String rewrittenQuery,
        List<MatchedEntity> matchedEntities,
        Integer explicitYear,
        Integer resolvedYear,
        boolean multiYearQuery
) {

    public QueryRewriteResult {
        matchedEntities = List.copyOf(matchedEntities);
    }

    public QueryRewriteResult(String originalQuery,
                              String rewrittenQuery,
                              List<MatchedEntity> matchedEntities) {
        this(originalQuery, rewrittenQuery, matchedEntities, null, null, false);
    }

    public QueryRewriteResult withResolvedYear(Integer year) {
        return new QueryRewriteResult(
                originalQuery,
                rewrittenQuery,
                matchedEntities,
                explicitYear,
                year,
                multiYearQuery
        );
    }
}
