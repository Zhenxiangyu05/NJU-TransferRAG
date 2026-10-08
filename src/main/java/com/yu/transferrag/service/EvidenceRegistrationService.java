package com.yu.transferrag.service;

import com.yu.transferrag.entity.*;
import com.yu.transferrag.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Service
public class EvidenceRegistrationService {
 private final DocumentRepository documents;
 private final ImportTaskRepository tasks;
 private final ImportTaskStateService state;
 public EvidenceRegistrationService(DocumentRepository documents,ImportTaskRepository tasks,ImportTaskStateService state) {
  this.documents=documents;this.tasks=tasks;this.state=state;
 }
 @Transactional
 public Registration register(Document prepared) {
  var existing=documents.findByFileHash(prepared.getFileHash());
  if(existing.isPresent()) return existing(existing.get().getId());
  if(prepared.getId()!=null) throw new IllegalStateException("Prepared document disappeared");
  Document saved=documents.saveAndFlush(prepared);
  ImportTask task=state.create(saved,false);
  return new Registration(task.getId(),saved.getId(),false,saved.getFilePath());
 }
 @Transactional
 public Registration resolveHash(String hash) {
  Document found=documents.findByFileHash(hash).orElseThrow(()->new IllegalStateException("Registration could not be confirmed"));
  return existing(found.getId());
 }
 private Registration existing(Long documentId) {
  // Registration and the one-task-per-document constraint are serialized by this row lock.
  Document d=documents.lockById(documentId).orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND));
  if(d.getDocumentRole()!=DocumentRole.EVIDENCE) throw new ResponseStatusException(HttpStatus.CONFLICT,"Only Evidence is supported");
  ImportTask task=tasks.findByDocument_Id(documentId).orElseGet(()->state.create(d,true));
  return new Registration(task.getId(),d.getId(),true,d.getFilePath());
 }
 public record Registration(long taskId,long documentId,boolean duplicate,String filePath) {}
}
