package com.yu.transferrag.service;

import com.yu.transferrag.entity.*;
import com.yu.transferrag.repository.EvidenceIndexSnapshotRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.Objects;

@Service
public class EvidenceSnapshotStateService {
    private final ImportTaskStateService state;
    private final EvidenceIndexSnapshotRepository snapshots;
    public EvidenceSnapshotStateService(ImportTaskStateService state, EvidenceIndexSnapshotRepository snapshots) {
        this.state=state;this.snapshots=snapshots;
    }
    @Transactional
    public Frozen load(ImportTaskStateService.Lease lease) {
        var task=state.requireOwned(lease);
        if(task.getTaskKind()!=ImportTaskKind.NEW_EVIDENCE || task.getStage()!=ImportTaskStage.INDEXING
                || task.getDocument().getDocumentRole()!=DocumentRole.EVIDENCE)
            throw new EvidenceImportProcessor.StageFailure("SNAPSHOT_REQUIRES_NEW_EVIDENCE",false);
        var row=snapshots.findById(lease.taskId()).orElseThrow(() ->
            new EvidenceImportProcessor.StageFailure("MISSING_INDEX_SNAPSHOT_REQUIRES_REVIEW",false));
        var input=EvidenceSnapshotCodec.decode(row.getInputJson(),row.getInputSha256(),EvidenceSnapshotCodec.Input.class);
        if(input.version()!=1 || !Objects.equals(task.getChunkCount(),input.points().size()))
            throw new EvidenceImportProcessor.StageFailure("SNAPSHOT_CORRUPT",false);
        var wire=row.getPointsJson()==null ? null : EvidenceSnapshotCodec.decode(row.getPointsJson(),row.getPointsSha256(),EvidenceSnapshotCodec.Wire.class);
        return new Frozen(input,wire);
    }
    @Transactional
    public EvidenceSnapshotCodec.Wire freeze(ImportTaskStateService.Lease lease, EvidenceSnapshotCodec.Wire proposed) {
        var frozen=load(lease); // Same task lock serializes publication and validates owner/generation.
        if(frozen.wire()!=null) return frozen.wire();
        if(proposed.version()!=1 || proposed.dimension()<=0 || proposed.points().size()!=frozen.input().points().size()
                || !Objects.equals(proposed.model(),frozen.input().model())
                || !Objects.equals(proposed.collection(),frozen.input().collection()))
            throw new EvidenceImportProcessor.StageFailure("SNAPSHOT_CORRUPT",false);
        var parsed=EvidenceSnapshotCodec.points(proposed);
        for(int n=0;n<parsed.size();n++) if(!parsed.get(n).getId().getUuid().equals(frozen.input().points().get(n).id()))
            throw new EvidenceImportProcessor.StageFailure("SNAPSHOT_CORRUPT",false);
        String json=EvidenceSnapshotCodec.encode(proposed);
        snapshots.findById(lease.taskId()).orElseThrow().freezePoints(json,EvidenceSnapshotCodec.sha(json));
        snapshots.flush();
        return proposed;
    }
    public record Frozen(EvidenceSnapshotCodec.Input input,EvidenceSnapshotCodec.Wire wire) {}
}
