package com.yu.transferrag.service;

import com.yu.transferrag.dto.MatchedEntity;
import com.yu.transferrag.dto.QueryRewriteResult;
import com.yu.transferrag.entity.EntityAlias;
import com.yu.transferrag.repository.EntityAliasRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class QueryRewriteService {

    private static final Pattern YEAR_PATTERN = Pattern.compile("(?<!\\d)((?:19|20)\\d{2})(?!\\d)");
    private static final List<String> CURRENT_YEAR_EXPRESSIONS = List.of("今年", "本年度", "当前");

    private final EntityAliasRepository entityAliasRepository;

    public QueryRewriteService(EntityAliasRepository entityAliasRepository) {
        this.entityAliasRepository = entityAliasRepository;
    }

    @Transactional(readOnly = true)
    public String rewrite(String query) {
        return rewriteInternal(query).rewrittenQuery();
    }

    @Transactional(readOnly = true)
    public QueryRewriteResult rewriteWithContext(String query) {
        return rewriteInternal(query);
    }

    private QueryRewriteResult rewriteInternal(String query) {
        if (query == null || query.isBlank()) {
            throw new IllegalArgumentException("query 不能为空");
        }

        YearUnderstanding yearUnderstanding = understandYear(query);
        List<EntityAlias> aliases = entityAliasRepository.findAll();
        if (aliases.isEmpty()) {
            return toResult(query, query, List.of(), yearUnderstanding);
        }

        Map<MatchPosition, MatchDetails> detailsByPosition = new LinkedHashMap<>();
        Set<MatchedEntity> alreadyMatchedEntities = new LinkedHashSet<>();
        for (EntityAlias entityAlias : aliases) {
            String standardName = normalize(entityAlias.getStandardName());
            String alias = normalize(entityAlias.getAlias());
            String entityType = normalize(entityAlias.getEntityType());
            if (standardName == null || entityType == null) {
                continue;
            }

            MatchedEntity matchedEntity = new MatchedEntity(
                    standardName,
                    entityType,
                    normalize(entityAlias.getDepartment())
            );
            addMatchedEntityIfPresent(query, standardName, matchedEntity, alreadyMatchedEntities);
            addMatchedEntityIfPresent(query, alias, matchedEntity, alreadyMatchedEntities);

            // 没有可靠别名时仍可通过标准名称识别实体和 department，但不改写查询。
            if (alias == null || standardName.equals(alias)) {
                continue;
            }
            if (alreadyExpanded(query, standardName, alias)) {
                alreadyMatchedEntities.add(matchedEntity);
                continue;
            }

            addMatches(query, standardName, alias, matchedEntity, detailsByPosition);
            addMatches(query, alias, standardName, matchedEntity, detailsByPosition);
        }

        List<RewriteMatch> matches = detailsByPosition.entrySet().stream()
                .map(entry -> new RewriteMatch(
                        entry.getKey().start(),
                        entry.getKey().end(),
                        List.copyOf(entry.getValue().additions()),
                        List.copyOf(entry.getValue().matchedEntities())
                ))
                .sorted(Comparator.comparingInt(RewriteMatch::length)
                        .reversed()
                        .thenComparingInt(RewriteMatch::start))
                .toList();

        List<RewriteMatch> selectedMatches = selectNonOverlapping(matches);
        if (selectedMatches.isEmpty()) {
            return toResult(
                    query,
                    query,
                    List.copyOf(alreadyMatchedEntities),
                    yearUnderstanding
            );
        }
        selectedMatches.sort(Comparator.comparingInt(RewriteMatch::start));

        Set<MatchedEntity> matchedEntities = new LinkedHashSet<>(alreadyMatchedEntities);
        selectedMatches.forEach(match -> matchedEntities.addAll(match.matchedEntities()));
        return toResult(
                query,
                applyMatches(query, selectedMatches),
                List.copyOf(matchedEntities),
                yearUnderstanding
        );
    }

    private QueryRewriteResult toResult(String originalQuery,
                                        String rewrittenQuery,
                                        List<MatchedEntity> matchedEntities,
                                        YearUnderstanding yearUnderstanding) {
        return new QueryRewriteResult(
                originalQuery,
                rewrittenQuery,
                matchedEntities,
                yearUnderstanding.explicitYear(),
                yearUnderstanding.resolvedYear(),
                yearUnderstanding.multiYearQuery()
        );
    }

    private YearUnderstanding understandYear(String query) {
        Set<Integer> mentionedYears = new LinkedHashSet<>();
        Matcher matcher = YEAR_PATTERN.matcher(query);
        while (matcher.find()) {
            mentionedYears.add(Integer.valueOf(matcher.group(1)));
        }

        int currentYear = LocalDate.now().getYear();
        boolean usesCurrentYearExpression = CURRENT_YEAR_EXPRESSIONS.stream()
                .anyMatch(query::contains);

        Set<Integer> resolvedCandidates = new LinkedHashSet<>(mentionedYears);
        if (usesCurrentYearExpression) {
            resolvedCandidates.add(currentYear);
        }

        if (resolvedCandidates.size() > 1) {
            return new YearUnderstanding(null, null, true);
        }
        if (mentionedYears.size() == 1) {
            Integer explicitYear = mentionedYears.iterator().next();
            return new YearUnderstanding(explicitYear, explicitYear, false);
        }
        if (usesCurrentYearExpression) {
            return new YearUnderstanding(null, currentYear, false);
        }
        return new YearUnderstanding(null, null, false);
    }

    private void addMatchedEntityIfPresent(String query,
                                           String name,
                                           MatchedEntity matchedEntity,
                                           Set<MatchedEntity> matchedEntities) {
        if (name != null && query.contains(name)) {
            matchedEntities.add(matchedEntity);
        }
    }

    private void addMatches(String query,
                            String matchedName,
                            String relatedName,
                            MatchedEntity matchedEntity,
                            Map<MatchPosition, MatchDetails> detailsByPosition) {
        int fromIndex = 0;
        while (fromIndex < query.length()) {
            int start = query.indexOf(matchedName, fromIndex);
            if (start < 0) {
                return;
            }

            int end = start + matchedName.length();
            MatchDetails details = detailsByPosition.computeIfAbsent(
                    new MatchPosition(start, end),
                    ignored -> new MatchDetails()
            );
            details.additions().add(relatedName);
            details.matchedEntities().add(matchedEntity);
            fromIndex = end;
        }
    }

    private List<RewriteMatch> selectNonOverlapping(List<RewriteMatch> matches) {
        List<RewriteMatch> selected = new ArrayList<>();
        for (RewriteMatch candidate : matches) {
            boolean overlaps = selected.stream()
                    .anyMatch(existing -> candidate.start() < existing.end()
                            && existing.start() < candidate.end());
            if (!overlaps) {
                selected.add(candidate);
            }
        }
        return selected;
    }

    private String applyMatches(String query, List<RewriteMatch> matches) {
        StringBuilder rewritten = new StringBuilder(query.length() + matches.size() * 16);
        int currentIndex = 0;

        for (RewriteMatch match : matches) {
            rewritten.append(query, currentIndex, match.start());
            rewritten.append(query, match.start(), match.end())
                    .append('（')
                    .append(String.join("、", match.additions()))
                    .append('）');
            currentIndex = match.end();
        }
        rewritten.append(query, currentIndex, query.length());
        return rewritten.toString();
    }

    private boolean alreadyExpanded(String query, String standardName, String alias) {
        return query.contains(standardName + "（" + alias + "）")
                || query.contains(alias + "（" + standardName + "）")
                || query.contains(standardName + "(" + alias + ")")
                || query.contains(alias + "(" + standardName + ")");
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record MatchPosition(int start, int end) {
    }

    private static final class MatchDetails {

        private final Set<String> additions = new LinkedHashSet<>();
        private final Set<MatchedEntity> matchedEntities = new LinkedHashSet<>();

        private Set<String> additions() {
            return additions;
        }

        private Set<MatchedEntity> matchedEntities() {
            return matchedEntities;
        }
    }

    private record RewriteMatch(
            int start,
            int end,
            List<String> additions,
            List<MatchedEntity> matchedEntities
    ) {

        private int length() {
            return end - start;
        }
    }

    private record YearUnderstanding(
            Integer explicitYear,
            Integer resolvedYear,
            boolean multiYearQuery
    ) {
    }
}
