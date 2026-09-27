package com.yu.transferrag.service;

import org.springframework.ai.vectorstore.filter.Filter;

import java.util.List;
import java.util.Map;

/**
 * Internal search boundary that accepts an already computed query vector.
 * This keeps a Canonical search and its Evidence fallback on one embedding.
 */
public interface PrecomputedVectorSearch {

    float[] embed(String query);

    List<VectorMatch> search(float[] queryEmbedding, int topK, Filter.Expression filter);

    record VectorMatch(String content, Map<String, Object> metadata, Double score) {
    }
}
