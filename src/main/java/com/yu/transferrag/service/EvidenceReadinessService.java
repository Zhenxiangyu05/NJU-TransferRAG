package com.yu.transferrag.service;

import com.yu.transferrag.dto.SearchResultResponse;
import com.yu.transferrag.entity.*;
import com.yu.transferrag.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

/** MySQL is the publication authority; Qdrant payload alone never grants readiness. */
@Service
public class EvidenceReadinessService {
    private final DocumentRepository documents;
    public EvidenceReadinessService(DocumentRepository documents) { this.documents = documents; }

    @Transactional(readOnly = true)
    public Set<Long> readyDocumentIds(Collection<Long> ids) {
        Set<Long> requested = new HashSet<>();
        for (Long id : ids) if (id != null) requested.add(id);
        if (requested.isEmpty()) return Set.of();
        try {
            Set<Long> ready = new HashSet<>();
            for (DocumentReadinessView row : documents.findReadinessByIds(requested)) {
                if (requested.contains(row.getDocumentId()) && row.getDocumentRole() == DocumentRole.EVIDENCE
                        && (row.getTaskId() == null
                        || row.getTaskKind() == ImportTaskKind.LEGACY_VERIFICATION
                        || (row.getTaskKind() == ImportTaskKind.NEW_EVIDENCE
                            && row.getStatus() == ImportTaskStatus.SUCCEEDED))) ready.add(row.getDocumentId());
            }
            return Set.copyOf(ready);
        } catch (RuntimeException failure) {
            // Do not expose SQL, credentials or provider content in the public error.
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Evidence readiness unavailable");
        }
    }

    @Transactional(readOnly = true)
    public List<SearchResultResponse> filterCandidates(List<SearchResultResponse> candidates) {
        Set<Long> ready = readyDocumentIds(candidates.stream().map(SearchResultResponse::getDocumentId).toList());
        return candidates.stream().filter(c -> c.getDocumentRole() == DocumentRole.EVIDENCE
                && ready.contains(c.getDocumentId())).toList();
    }
}
