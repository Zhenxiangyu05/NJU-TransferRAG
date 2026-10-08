package com.yu.transferrag.service;

import io.qdrant.client.*;
import io.qdrant.client.grpc.Points.*;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.qdrant.QdrantVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import java.util.concurrent.TimeUnit;

/** No VectorStore.add here: replay frozen protobuf points without re-embedding. */
@Service
public class FrozenEvidenceIndexer {
    private final EvidenceSnapshotStateService snapshots;
    private final EmbeddingModel embedding;
    private final VectorStore store;
    private final String collection;
    private final String model;
    public FrozenEvidenceIndexer(EvidenceSnapshotStateService snapshots, EmbeddingModel embedding, VectorStore store,
        @Value("${spring.ai.vectorstore.qdrant.collection-name}") String collection,
        @Value("${spring.ai.openai.embedding.model}") String model) {
        this.snapshots=snapshots;this.embedding=embedding;this.store=store;this.collection=collection;this.model=model;
    }
    public int index(ImportTaskStateService.Lease lease) {
        if(TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Network inside transaction");
        var frozen=snapshots.load(lease);
        if(!collection.equals(frozen.input().collection()) || !model.equals(frozen.input().model()))
            throw new EvidenceImportProcessor.StageFailure("SNAPSHOT_TARGET_CHANGED_REQUIRES_REVIEW",false);
        var wire=frozen.wire();
        if(wire==null) {
            var input=frozen.input();
            List<float[]> vectors=embedding.embed(input.points().stream().map(EvidenceSnapshotCodec.TextPoint::text).toList());
            if(vectors.size()!=input.points().size()) throw new EvidenceImportProcessor.StageFailure("EMBEDDING_SHAPE_INVALID",false);
            int dimension=vectors.getFirst().length;
            if(dimension==0) throw new EvidenceImportProcessor.StageFailure("EMBEDDING_SHAPE_INVALID",false);
            List<String> points=new ArrayList<>();
            for(int n=0;n<vectors.size();n++) {
                float[] vector=vectors.get(n);
                if(vector.length!=dimension) throw new EvidenceImportProcessor.StageFailure("EMBEDDING_SHAPE_INVALID",false);
                for(float value:vector) if(!Float.isFinite(value)) throw new EvidenceImportProcessor.StageFailure("EMBEDDING_SHAPE_INVALID",false);
                var text=input.points().get(n);
                var point=PointStruct.newBuilder().setId(PointIdFactory.id(UUID.fromString(text.id())))
                    .setVectors(VectorsFactory.vectors(vector));
                text.metadata().forEach((key,value) -> {
                    if(value instanceof String s) point.putPayload(key,ValueFactory.value(s));
                    else if(value instanceof Number number) point.putPayload(key,ValueFactory.value(number.longValue()));
                    else throw new EvidenceImportProcessor.StageFailure("SNAPSHOT_METADATA_UNSUPPORTED",false);
                });
                point.putPayload(QdrantVectorStore.DEFAULT_CONTENT_FIELD_NAME,ValueFactory.value(text.text()));
                points.add(Base64.getEncoder().encodeToString(point.build().toByteArray()));
            }
            wire=snapshots.freeze(lease,new EvidenceSnapshotCodec.Wire(1,collection,model,dimension,List.copyOf(points)));
        }
        if(!collection.equals(wire.collection()) || !model.equals(wire.model()))
            throw new EvidenceImportProcessor.StageFailure("SNAPSHOT_TARGET_CHANGED_REQUIRES_REVIEW",false);
        // Recheck ownership before sending; an in-flight write cannot be fenced, but its bytes are identical.
        snapshots.load(lease);
        write(wire);
        return wire.points().size();
    }
    protected void write(EvidenceSnapshotCodec.Wire wire) {
        var client=store.<QdrantClient>getNativeClient().orElseThrow(() -> new IllegalStateException("Native Qdrant required"));
        try {
            var points=EvidenceSnapshotCodec.points(wire);
            for(int offset=0;offset<points.size();offset+=64) {
                var result=client.upsertAsync(UpsertPoints.newBuilder().setCollectionName(wire.collection())
                    .setWait(true).addAllPoints(points.subList(offset,Math.min(offset+64,points.size()))).build()).get(60,TimeUnit.SECONDS);
                if(result.getStatus()!=UpdateStatus.Completed) throw new IllegalStateException("Qdrant write not complete");
            }
        } catch(InterruptedException error) { Thread.currentThread().interrupt();throw new IllegalStateException("Index interrupted"); }
        catch(Exception error) { throw new IllegalStateException("Frozen index write failed"); }
    }
}
