package com.yu.transferrag.cache;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.*;
import java.util.List;

/** Only the Qdrant query's single-string overload uses cache; every document/batch path is delegated. */
public final class QueryOnlyEmbeddingModel implements EmbeddingModel {
    private final EmbeddingModel delegate;
    private final QueryEmbeddingCache cache;
    public QueryOnlyEmbeddingModel(EmbeddingModel delegate,QueryEmbeddingCache cache) { this.delegate=delegate;this.cache=cache; }
    @Override public float[] embed(String text) { return cache.embed(text); }
    @Override public EmbeddingResponse call(EmbeddingRequest request) { return delegate.call(request); }
    @Override public float[] embed(Document document) { return delegate.embed(document); }
    @Override public List<float[]> embed(List<String> texts) { return delegate.embed(texts); }
    @Override public List<float[]> embed(List<Document> documents,EmbeddingOptions options,BatchingStrategy strategy) { return delegate.embed(documents,options,strategy); }
    @Override public EmbeddingResponse embedForResponse(List<String> texts) { return delegate.embedForResponse(texts); }
    @Override public String getEmbeddingContent(Document document) { return delegate.getEmbeddingContent(document); }
    @Override public int dimensions() { return delegate.dimensions(); }
}
