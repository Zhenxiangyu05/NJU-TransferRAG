package com.yu.transferrag.service;

import io.qdrant.client.grpc.Common.Condition;
import io.qdrant.client.grpc.Common.Filter;
import org.junit.jupiter.api.Test;
import org.springframework.ai.vectorstore.filter.FilterExpressionBuilder;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QdrantPrecomputedVectorSearchTest {

    private final QdrantPrecomputedVectorSearch adapter =
            new QdrantPrecomputedVectorSearch(null, null, "transfer_chunks");

    @Test
    void notEqualExcludesCanonicalButKeepsEvidenceAndMissingLegacyRole() {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();

        Filter filter = adapter.toQdrantFilter(builder.ne("documentRole", "CANONICAL").build());

        assertEquals(0, filter.getMustCount());
        assertEquals(0, filter.getShouldCount());
        assertEquals(1, filter.getMustNotCount());
        assertKeywordMatch(filter.getMustNot(0), "documentRole", "CANONICAL");
        assertTrue(matches(filter, Map.of("documentRole", "EVIDENCE")));
        assertTrue(matches(filter, Map.of()));
        assertFalse(matches(filter, Map.of("documentRole", "CANONICAL")));
    }

    @Test
    void departmentAndNotEqualPreserveBothConditions() {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();

        Filter filter = adapter.toQdrantFilter(builder.and(
                builder.eq("department", "软件学院"),
                builder.ne("documentRole", "CANONICAL")
        ).build());

        assertEquals(2, filter.getMustCount());
        assertKeywordMatch(filter.getMust(0).getFilter().getMust(0), "department", "软件学院");
        assertKeywordMatch(filter.getMust(1).getFilter().getMustNot(0), "documentRole", "CANONICAL");
        assertTrue(matches(filter, Map.of("department", "软件学院", "documentRole", "EVIDENCE")));
        assertTrue(matches(filter, Map.of("department", "软件学院")));
        assertFalse(matches(filter, Map.of("department", "软件学院", "documentRole", "CANONICAL")));
        assertFalse(matches(filter, Map.of("department", "数学学院")));
    }

    @Test
    void notEqualWorksInsideExistingAndOrMetadataFilters() {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();

        Filter filter = adapter.toQdrantFilter(builder.and(
                builder.ne("documentRole", "CANONICAL"),
                builder.or(builder.eq("policyYear", 2026), builder.eq("cohortYear", 2025))
        ).build());

        assertTrue(matches(filter, Map.of("policyYear", 2026)));
        assertTrue(matches(filter, Map.of("cohortYear", 2025, "documentRole", "EVIDENCE")));
        assertFalse(matches(filter, Map.of("policyYear", 2026, "documentRole", "CANONICAL")));
        assertFalse(matches(filter, Map.of("cohortYear", 2024)));
    }

    @Test
    void unsupportedOperatorStillFailsExplicitly() {
        FilterExpressionBuilder builder = new FilterExpressionBuilder();

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> adapter.toQdrantFilter(builder.gt("policyYear", 2025).build()));

        assertTrue(error.getMessage().contains("GT"));
    }

    private void assertKeywordMatch(Condition condition, String key, String value) {
        assertEquals(key, condition.getField().getKey());
        assertEquals(value, condition.getField().getMatch().getKeyword());
    }

    // Evaluate the emitted Qdrant protobuf shape for the values relevant to these tests.
    private boolean matches(Filter filter, Map<String, Object> payload) {
        return filter.getMustList().stream().allMatch(condition -> matches(condition, payload))
                && (filter.getShouldCount() == 0
                    || filter.getShouldList().stream().anyMatch(condition -> matches(condition, payload)))
                && filter.getMustNotList().stream().noneMatch(condition -> matches(condition, payload));
    }

    private boolean matches(Condition condition, Map<String, Object> payload) {
        if (condition.hasFilter()) {
            return matches(condition.getFilter(), payload);
        }
        if (!condition.hasField()) {
            throw new IllegalArgumentException("Unexpected Qdrant condition");
        }
        Object value = payload.get(condition.getField().getKey());
        if (condition.getField().getMatch().hasKeyword()) {
            return condition.getField().getMatch().getKeyword().equals(value);
        }
        return value instanceof Number number
                && condition.getField().getMatch().getInteger() == number.longValue();
    }
}
