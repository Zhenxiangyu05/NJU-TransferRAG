package com.yu.transferrag.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;

@Service
public class EvidenceLegacyVerifier {
 private final QdrantMetadataReader reader;
 private final Path uploadRoot;
 public EvidenceLegacyVerifier(QdrantMetadataReader reader,@Value("${app.upload-dir}") String uploadRoot) {
  this.reader=reader;this.uploadRoot=Path.of(uploadRoot).toAbsolutePath().normalize();
 }
 public void verify(EvidenceChunkPersistenceService.Snapshot snapshot) {
  if(snapshot.vectors().isEmpty() || snapshot.filePath()==null || snapshot.fileHash()==null) reject();
  try {
   Path root=uploadRoot.toRealPath(),file=Path.of(snapshot.filePath()).toRealPath();
   if(!file.startsWith(root)||file.equals(root)||!Files.isRegularFile(file)) reject();
   MessageDigest sha=MessageDigest.getInstance("SHA-256");
   try(var in=Files.newInputStream(file)) {
    byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)sha.update(b,0,n);
   }
   if(!HexFormat.of().formatHex(sha.digest()).equalsIgnoreCase(snapshot.fileHash())) reject();
  } catch(EvidenceImportProcessor.StageFailure failure) { throw failure; }
  catch(Exception error) { reject(); }
  // All network/file operations occur after the short snapshot transaction has returned.
  var points=reader.findByDocumentId(snapshot.documentId());
  if(points.size()!=snapshot.vectors().size()) reject();
  Map<String,Map<String,Object>> byChunk=new HashMap<>();
  for(var p:points) {
   Object id=p.metadata().get("chunkId");
   if(id==null || byChunk.put(id.toString(),p.metadata())!=null) reject();
  }
  for(var vector:snapshot.vectors()) {
   var metadata=vector.getMetadata();
   var actual=byChunk.get(metadata.get("chunkId").toString());
   if(actual==null) reject();
   for(var entry:metadata.entrySet()) {
    if(entry.getKey().equals("documentRole") || entry.getKey().equals("section")) continue; // Legacy payload may lack these.
    if(!Objects.equals(String.valueOf(entry.getValue()),String.valueOf(actual.get(entry.getKey())))) reject();
   }
  }
 }
 private static void reject() { throw new EvidenceImportProcessor.StageFailure("LEGACY_STATE_REQUIRES_REVIEW",false); }
}
