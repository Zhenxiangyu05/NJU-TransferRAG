package com.yu.transferrag.service;

import com.yu.transferrag.entity.*;
import com.yu.transferrag.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;
import java.util.*;

@Service
public class EvidenceChunkPersistenceService {
 private final ImportTaskStateService state;
 private final ChunkRepository chunks;
 private final ImportTaskRepository tasks;
 private final VectorIndexService index;
 private final EvidenceIndexSnapshotRepository snapshots;
 @org.springframework.beans.factory.annotation.Value("${spring.ai.vectorstore.qdrant.collection-name:transfer_chunks}")
 private String collection="transfer_chunks";
 @org.springframework.beans.factory.annotation.Value("${spring.ai.openai.embedding.model:unconfigured}")
 private String model="unconfigured";
 public EvidenceChunkPersistenceService(ImportTaskStateService state,ChunkRepository chunks,ImportTaskRepository tasks,VectorIndexService index,EvidenceIndexSnapshotRepository snapshots) {
  this.state=state;this.chunks=chunks;this.tasks=tasks;this.index=index;this.snapshots=snapshots;
 }
 @Transactional
 public void persistFirst(ImportTaskStateService.Lease lease,List<Chunk> prepared) {
  ImportTask task=state.requireOwned(lease);
  if(task.getStage()!=ImportTaskStage.CHUNKING) throw new ImportTaskStateService.LostLease();
  if(task.getTaskKind()!=ImportTaskKind.NEW_EVIDENCE || task.getDocument().getDocumentRole()!=DocumentRole.EVIDENCE
     || snapshots.existsById(lease.taskId()))
   throw new EvidenceImportProcessor.StageFailure("CHUNK_STATE_REQUIRES_REVIEW",false);
  if(prepared.isEmpty() || !chunks.findByDocument_IdOrderByChunkIndexAsc(lease.documentId()).isEmpty())
   throw new EvidenceImportProcessor.StageFailure("CHUNK_STATE_REQUIRES_REVIEW",false);
  for(Chunk c:prepared) {
   if(c.getId()!=null) throw new IllegalArgumentException("Only new chunks may be persisted");
   c.setDocument(task.getDocument());
  }
  chunks.saveAllAndFlush(prepared);
  List<EvidenceSnapshotCodec.TextPoint> input=prepared.stream().map(c -> {
   var base=index.toVectorDocument(c);
   return new EvidenceSnapshotCodec.TextPoint(pointId(lease.documentId(),c.getId()),base.getText(),base.getMetadata());
  }).toList();
  String json=EvidenceSnapshotCodec.encode(new EvidenceSnapshotCodec.Input(1,collection,model,input));
  snapshots.saveAndFlush(new EvidenceIndexSnapshot(lease.taskId(),json,EvidenceSnapshotCodec.sha(json)));
  task.setChunkCount(prepared.size());task.setStage(ImportTaskStage.INDEXING);task.setUpdatedAt(tasks.databaseNow());
 }
 @Transactional
 public Snapshot snapshot(ImportTaskStateService.Lease lease) {
  ImportTask task=state.requireOwned(lease);
  Document d=task.getDocument();
  if(d.getDocumentRole()!=DocumentRole.EVIDENCE)
   throw new EvidenceImportProcessor.StageFailure("DOCUMENT_ROLE_REQUIRES_REVIEW",false);
  List<Chunk> rows=chunks.findByDocument_IdOrderByChunkIndexAsc(d.getId());
  if(task.getStage()==ImportTaskStage.INDEXING && !Objects.equals(task.getChunkCount(),rows.size()))
   throw new EvidenceImportProcessor.StageFailure("CHUNK_STATE_REQUIRES_REVIEW",false);
  List<org.springframework.ai.document.Document> vectors=new ArrayList<>();
  for(int n=0;n<rows.size();n++) {
   Chunk c=rows.get(n);
   if(!Objects.equals(c.getChunkIndex(),n)) throw new EvidenceImportProcessor.StageFailure("CHUNK_STATE_REQUIRES_REVIEW",false);
   var base=index.toVectorDocument(c);
   vectors.add(org.springframework.ai.document.Document.builder().id(pointId(d.getId(),c.getId()))
     .text(base.getText()).metadata(base.getMetadata()).build());
  }
  return new Snapshot(d.getId(),d.getFilePath(),d.getFileHash(),List.copyOf(vectors));
 }
 public static String pointId(long documentId,long chunkId) {
  return UUID.nameUUIDFromBytes(("nju-compass:evidence:"+documentId+":"+chunkId).getBytes(StandardCharsets.UTF_8)).toString();
 }
 public record Snapshot(long documentId,String filePath,String fileHash,List<org.springframework.ai.document.Document> vectors) {}
}
