package com.yu.transferrag.service;

import com.yu.transferrag.entity.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
public class EvidenceTaskProcessor implements EvidenceImportProcessor {
 private final ImportTaskStateService state;
 private final ChunkService chunks;
 private final EvidenceChunkPersistenceService persistence;
 private final FrozenEvidenceIndexer index;
 private final EvidenceLegacyVerifier legacy;
 public EvidenceTaskProcessor(ImportTaskStateService state,ChunkService chunks,EvidenceChunkPersistenceService persistence,FrozenEvidenceIndexer index,EvidenceLegacyVerifier legacy) {
  this.state=state;this.chunks=chunks;this.persistence=persistence;this.index=index;this.legacy=legacy;
 }
 @Override public Result process(ImportTaskStateService.Lease lease) {
  if(TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Import worker cannot run inside a transaction");
  if(lease.stage()==ImportTaskStage.VERIFY_EXISTING) {
   try {
    var snapshot=persistence.snapshot(lease);
    legacy.verify(snapshot);
    return new Result(snapshot.vectors().size(),snapshot.vectors().size());
   } catch(ImportTaskStateService.LostLease | StageFailure e) { throw e; }
   catch(RuntimeException error) { throw new StageFailure("LEGACY_VERIFICATION_UNAVAILABLE",true); }
  }
  if(lease.stage()!=ImportTaskStage.INDEXING) {
   java.util.List<Chunk> prepared;
   try { prepared=chunks.prepareEvidenceChunks(lease.documentId()); }
   catch(RuntimeException error) { throw new StageFailure("PARSING_FAILED",true); }
   state.stage(lease,ImportTaskStage.CHUNKING);
   try { persistence.persistFirst(lease,prepared); }
   catch(ImportTaskStateService.LostLease | StageFailure e) { throw e; }
   catch(RuntimeException error) { throw new StageFailure("CHUNK_PERSISTENCE_FAILED",true); }
  }
  try {
   if(Thread.currentThread().isInterrupted()) throw new StageFailure("EXECUTION_CANCELLED",true);
   int indexed=index.index(lease);
   return new Result(indexed,indexed);
  } catch(ImportTaskStateService.LostLease | StageFailure e) { throw e; }
  catch(RuntimeException error) { throw new StageFailure("EMBEDDING_OR_INDEX_FAILED",true); }
 }
}
