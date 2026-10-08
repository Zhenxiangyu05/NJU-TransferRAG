package com.yu.transferrag.service;

import com.yu.transferrag.entity.DocumentRole;
import com.yu.transferrag.repository.DocumentRepository;
import com.yu.transferrag.repository.ImportTaskRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ImportTaskManualGuard {
    private final DocumentRepository documents;
    private final ImportTaskRepository tasks;
    private final boolean enabled;
    public ImportTaskManualGuard(DocumentRepository documents, ImportTaskRepository tasks,
                                 @Value("${app.import-tasks.enabled:false}") boolean enabled) {
        this.documents = documents; this.tasks = tasks; this.enabled = enabled;
    }
    @Transactional(readOnly = true)
    public void check(long documentId) {
        var document = documents.findById(documentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if (document.getDocumentRole() == DocumentRole.EVIDENCE
                && (enabled || tasks.findByDocument_Id(documentId).isPresent()))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Use the Evidence import task instead of manual chunk/index");
    }
    /** Destructive service paths are never allowed for Evidence, including a no-task registration race. */
    @Transactional(readOnly = true)
    public void checkIndexMutation(long documentId) {
        var document=documents.findById(documentId).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND));
        if(document.getDocumentRole()==DocumentRole.EVIDENCE)
            throw new ResponseStatusException(HttpStatus.CONFLICT,"Evidence index is immutable; use import tasks or read-only legacy verification");
    }
}
