package com.yu.transferrag.service;

import com.yu.transferrag.dto.EntityRole;
import com.yu.transferrag.dto.MatchedEntity;
import com.yu.transferrag.dto.QueryRewriteResult;
import com.yu.transferrag.dto.ResolvedEntity;
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
    private static final List<String> AMBIGUOUS_SHORT_EXPRESSIONS = List.of("电子", "光电");

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
        Map<MatchPosition, MatchDetails> detailsByPosition = new LinkedHashMap<>();
        for (EntityAlias entityAlias : entityAliasRepository.findAll()) {
            addEntityMatches(query, entityAlias, detailsByPosition);
        }

        List<RewriteMatch> candidates = detailsByPosition.entrySet().stream()
                .map(entry -> new RewriteMatch(
                        entry.getKey().start(), entry.getKey().end(),
                        List.copyOf(entry.getValue().additions()),
                        List.copyOf(entry.getValue().descriptors())
                ))
                .sorted(Comparator.comparingInt(RewriteMatch::length)
                        .reversed().thenComparingInt(RewriteMatch::start))
                .toList();

        List<RewriteMatch> selectedMatches = selectNonOverlapping(candidates);
        selectedMatches.sort(Comparator.comparingInt(RewriteMatch::start));
        Resolution resolution = resolveSelectedEntities(query, selectedMatches);
        addUncoveredAmbiguousExpressions(query, selectedMatches, resolution);

        return new QueryRewriteResult(
                query,
                applyMatches(query, selectedMatches),
                List.copyOf(resolution.matchedEntities()),
                List.copyOf(resolution.resolvedEntities()),
                List.copyOf(resolution.departments()),
                List.copyOf(resolution.majors()),
                List.copyOf(resolution.ambiguousEntities()),
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

    private void addEntityMatches(String query,
                                  EntityAlias entityAlias,
                                  Map<MatchPosition, MatchDetails> detailsByPosition) {
        String standardName = normalize(entityAlias.getStandardName());
        String alias = normalize(entityAlias.getAlias());
        String entityType = normalize(entityAlias.getEntityType());
        if (standardName == null || entityType == null) {
            return;
        }

        String department = normalize(entityAlias.getDepartment());
        if (department == null && "DEPARTMENT".equalsIgnoreCase(entityType)) {
            department = standardName;
        }
        MatchedEntity matchedEntity = new MatchedEntity(standardName, entityType, department);
        addMatches(query, standardName, alias, matchedEntity, detailsByPosition);
        if (alias != null && !standardName.equals(alias)) {
            addMatches(query, alias, standardName, matchedEntity, detailsByPosition);
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
                    new MatchPosition(start, end), ignored -> new MatchDetails()
            );
            if (relatedName != null && !relatedName.equals(matchedName)) {
                details.additions().add(relatedName);
            }
            details.descriptors().add(new EntityDescriptor(matchedEntity));
            fromIndex = end;
        }
    }

    private List<RewriteMatch> selectNonOverlapping(List<RewriteMatch> matches) {
        List<RewriteMatch> selected = new ArrayList<>();
        for (RewriteMatch candidate : matches) {
            boolean overlaps = selected.stream().anyMatch(existing -> overlaps(candidate, existing));
            if (!overlaps) {
                selected.add(candidate);
            }
        }
        return selected;
    }

    private Resolution resolveSelectedEntities(String query, List<RewriteMatch> selectedMatches) {
        Resolution resolution = new Resolution();
        for (RewriteMatch match : selectedMatches) {
            Set<MatchedEntity> meanings = new LinkedHashSet<>();
            match.descriptors().forEach(descriptor -> meanings.add(descriptor.entity()));
            EntityRole role = meanings.size() > 1
                    ? EntityRole.AMBIGUOUS
                    : determineRole(query, match.start(), match.end());
            String matchedText = query.substring(match.start(), match.end());

            for (MatchedEntity entity : meanings) {
                resolution.matchedEntities().add(entity);
                resolution.resolvedEntities().add(new ResolvedEntity(
                        entity.standardName(), entity.entityType(), entity.department(), matchedText, role
                ));
                if (role == EntityRole.TARGET) {
                    addTarget(entity, resolution);
                }
            }
            if (role == EntityRole.AMBIGUOUS) {
                resolution.ambiguousEntities().add(matchedText);
            }
        }
        return resolution;
    }

    private void addTarget(MatchedEntity entity, Resolution resolution) {
        if (entity.department() != null && !entity.department().isBlank()) {
            resolution.departments().add(entity.department().trim());
        }
        if (("MAJOR".equalsIgnoreCase(entity.entityType())
                || "PROGRAM".equalsIgnoreCase(entity.entityType()))
                && entity.standardName() != null && !entity.standardName().isBlank()) {
            resolution.majors().add(entity.standardName().trim());
        }
    }

    private EntityRole determineRole(String query, int start, int end) {
        String before = query.substring(0, start).replaceAll("\\s+$", "");
        String after = query.substring(end).replaceAll("^\\s+", "");
        if (after.startsWith("之外") || before.endsWith("除了")) {
            return EntityRole.EXCLUDED;
        }
        if (before.endsWith("不同于") || before.endsWith("相比")) {
            return EntityRole.COMPARISON;
        }
        return EntityRole.TARGET;
    }

    private void addUncoveredAmbiguousExpressions(String query,
                                                  List<RewriteMatch> selectedMatches,
                                                  Resolution resolution) {
        for (String expression : AMBIGUOUS_SHORT_EXPRESSIONS) {
            int fromIndex = 0;
            while (fromIndex < query.length()) {
                int start = query.indexOf(expression, fromIndex);
                if (start < 0) {
                    break;
                }
                int end = start + expression.length();
                boolean covered = selectedMatches.stream()
                        .anyMatch(match -> start >= match.start() && end <= match.end());
                if (!covered) {
                    resolution.resolvedEntities().add(new ResolvedEntity(
                            expression, "AMBIGUOUS", null, expression, EntityRole.AMBIGUOUS
                    ));
                    resolution.ambiguousEntities().add(expression);
                }
                fromIndex = end;
            }
        }
    }

    private String applyMatches(String query, List<RewriteMatch> matches) {
        if (matches.isEmpty()) {
            return query;
        }
        StringBuilder rewritten = new StringBuilder(query.length() + matches.size() * 16);
        int currentIndex = 0;
        for (RewriteMatch match : matches) {
            rewritten.append(query, currentIndex, match.start());
            rewritten.append(query, match.start(), match.end());
            if (!match.additions().isEmpty()) {
                rewritten.append('（').append(String.join("、", match.additions())).append('）');
            }
            currentIndex = match.end();
        }
        rewritten.append(query, currentIndex, query.length());
        return rewritten.toString();
    }

    private boolean overlaps(RewriteMatch left, RewriteMatch right) {
        return left.start() < right.end() && right.start() < left.end();
    }

    private String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record MatchPosition(int start, int end) {
    }

    private record EntityDescriptor(MatchedEntity entity) {
    }

    private static final class MatchDetails {
        private final Set<String> additions = new LinkedHashSet<>();
        private final Set<EntityDescriptor> descriptors = new LinkedHashSet<>();
        private Set<String> additions() { return additions; }
        private Set<EntityDescriptor> descriptors() { return descriptors; }
    }

    private record RewriteMatch(int start, int end, List<String> additions,
                                List<EntityDescriptor> descriptors) {
        private int length() { return end - start; }
    }

    private static final class Resolution {
        private final Set<MatchedEntity> matchedEntities = new LinkedHashSet<>();
        private final List<ResolvedEntity> resolvedEntities = new ArrayList<>();
        private final Set<String> departments = new LinkedHashSet<>();
        private final Set<String> majors = new LinkedHashSet<>();
        private final Set<String> ambiguousEntities = new LinkedHashSet<>();
        private Set<MatchedEntity> matchedEntities() { return matchedEntities; }
        private List<ResolvedEntity> resolvedEntities() { return resolvedEntities; }
        private Set<String> departments() { return departments; }
        private Set<String> majors() { return majors; }
        private Set<String> ambiguousEntities() { return ambiguousEntities; }
    }

    private record YearUnderstanding(Integer explicitYear, Integer resolvedYear,
                                     boolean multiYearQuery) {
    }
}
