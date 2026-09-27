package com.yu.transferrag.service;

import com.yu.transferrag.dto.CanonicalImportRequest;
import com.yu.transferrag.dto.CanonicalImportResponse;
import com.yu.transferrag.entity.Chunk;
import com.yu.transferrag.entity.Document;
import com.yu.transferrag.entity.DocumentRole;
import com.yu.transferrag.entity.EvidenceRef;
import com.yu.transferrag.repository.ChunkRepository;
import com.yu.transferrag.repository.DocumentRepository;
import com.yu.transferrag.repository.EvidenceRefRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CanonicalImportService {

    private final DocumentRepository documentRepository;
    private final ChunkRepository chunkRepository;
    private final EvidenceRefRepository evidenceRefRepository;
    private final VectorIndexService vectorIndexService;

    public CanonicalImportService(DocumentRepository documentRepository,
                                  ChunkRepository chunkRepository,
                                  EvidenceRefRepository evidenceRefRepository,
                                  VectorIndexService vectorIndexService) {
        this.documentRepository = documentRepository;
        this.chunkRepository = chunkRepository;
        this.evidenceRefRepository = evidenceRefRepository;
        this.vectorIndexService = vectorIndexService;
    }

    /**
     * Validation is deliberately completed before a Canonical Document is
     * persisted, so invalid JSON cannot leave a partial knowledge card.
     */
    @Transactional
    public CanonicalImportResponse importCanonical(CanonicalImportRequest request) {
        validateRequest(request);
        Map<Long, Document> evidenceDocuments = loadAndValidateEvidenceDocuments(request);

        Document canonicalDocument = createCanonicalDocument(request);
        canonicalDocument = documentRepository.save(canonicalDocument);

        List<Chunk> chunks = createCanonicalChunks(canonicalDocument, request.sections());
        chunks = chunkRepository.saveAll(chunks);

        List<EvidenceRef> evidenceRefs = createEvidenceRefs(chunks, request.sections(), evidenceDocuments);
        evidenceRefRepository.saveAll(evidenceRefs);

        int indexedChunks;
        try {
            indexedChunks = vectorIndexService.indexDocument(canonicalDocument.getId());
        } catch (RuntimeException indexFailure) {
            compensateQdrant(canonicalDocument.getId(), indexFailure);
            throw indexFailure;
        }
        return new CanonicalImportResponse(
                canonicalDocument.getId(),
                DocumentRole.CANONICAL,
                Document.SOURCE_TYPE_CURATED,
                chunks.size(),
                evidenceRefs.size(),
                indexedChunks
        );
    }

    private Map<Long, Document> loadAndValidateEvidenceDocuments(CanonicalImportRequest request) {
        Set<Long> evidenceDocumentIds = new LinkedHashSet<>();
        for (CanonicalImportRequest.CanonicalSectionRequest section : request.sections()) {
            for (CanonicalImportRequest.CanonicalFactRequest fact : section.facts()) {
                for (CanonicalImportRequest.EvidenceRefRequest ref : fact.evidenceRefs()) {
                    evidenceDocumentIds.add(ref.sourceDocumentId());
                }
            }
        }

        Map<Long, Document> documentsById = new HashMap<>();
        documentRepository.findAllById(evidenceDocumentIds)
                .forEach(document -> documentsById.put(document.getId(), document));
        if (documentsById.size() != evidenceDocumentIds.size()) {
            Set<Long> missingIds = new LinkedHashSet<>(evidenceDocumentIds);
            missingIds.removeAll(documentsById.keySet());
            throw new IllegalArgumentException("EvidenceRef 引用了不存在的 sourceDocumentId: " + missingIds);
        }

        for (Document document : documentsById.values()) {
            if (document.getDocumentRole() != DocumentRole.EVIDENCE) {
                throw new IllegalArgumentException(
                        "EvidenceRef 只能引用 EVIDENCE Document: " + document.getId()
                );
            }
        }
        return documentsById;
    }

    private Document createCanonicalDocument(CanonicalImportRequest request) {
        Document document = new Document();
        document.setTitle(request.title().trim());
        document.setDepartment(request.department().trim());
        document.setYear(request.year());
        document.setScope(normalizeScope(request.scope()));
        document.setSourceType(Document.SOURCE_TYPE_CURATED);
        document.setDocumentRole(DocumentRole.CANONICAL);
        // status is the import lifecycle state. CURATED is represented by
        // documentRole/sourceType; successful documents retain UPLOADED.
        document.setStatus("UPLOADED");
        return document;
    }

    private List<Chunk> createCanonicalChunks(
            Document canonicalDocument,
            List<CanonicalImportRequest.CanonicalSectionRequest> sections) {
        List<Chunk> chunks = new ArrayList<>();
        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            CanonicalImportRequest.CanonicalSectionRequest section = sections.get(sectionIndex);
            Chunk chunk = new Chunk();
            chunk.setDocument(canonicalDocument);
            chunk.setChunkIndex(sectionIndex);
            chunk.setSection(section.section().trim());
            chunk.setContent(buildSectionContent(section));
            chunk.setPolicyYear(section.policyYear());
            chunk.setCohortYear(section.cohortYear());
            chunk.setDepartment(trimToNull(section.department()));
            chunk.setMajor(trimToNull(section.major()));
            chunks.add(chunk);
        }
        return chunks;
    }

    private List<EvidenceRef> createEvidenceRefs(
            List<Chunk> chunks,
            List<CanonicalImportRequest.CanonicalSectionRequest> sections,
            Map<Long, Document> evidenceDocuments) {
        List<EvidenceRef> references = new ArrayList<>();
        for (int sectionIndex = 0; sectionIndex < sections.size(); sectionIndex++) {
            Chunk canonicalChunk = chunks.get(sectionIndex);
            List<CanonicalImportRequest.CanonicalFactRequest> facts = sections.get(sectionIndex).facts();
            for (int factIndex = 0; factIndex < facts.size(); factIndex++) {
                for (CanonicalImportRequest.EvidenceRefRequest requestRef : facts.get(factIndex).evidenceRefs()) {
                    EvidenceRef reference = new EvidenceRef();
                    reference.setCanonicalChunk(canonicalChunk);
                    reference.setEvidenceDocument(evidenceDocuments.get(requestRef.sourceDocumentId()));
                    reference.setFactIndex(factIndex);
                    reference.setSourcePage(requestRef.page());
                    reference.setEvidenceText(requestRef.evidenceText().trim());
                    references.add(reference);
                }
            }
        }
        return references;
    }

    private String buildSectionContent(CanonicalImportRequest.CanonicalSectionRequest section) {
        StringBuilder content = new StringBuilder("主题：").append(section.section().trim());
        for (int factIndex = 0; factIndex < section.facts().size(); factIndex++) {
            CanonicalImportRequest.CanonicalFactRequest fact = section.facts().get(factIndex);
            content.append("\n[F").append(factIndex).append("] ").append(fact.text().trim());
        }
        return content.toString();
    }

    private void compensateQdrant(Long documentId, RuntimeException indexFailure) {
        try {
            vectorIndexService.removeDocumentIndex(documentId);
        } catch (RuntimeException cleanupFailure) {
            indexFailure.addSuppressed(cleanupFailure);
        }
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return Document.SCOPE_DEPARTMENT;
        }
        String normalized = scope.trim().toUpperCase(Locale.ROOT);
        if (!Document.SCOPE_DEPARTMENT.equals(normalized) && !Document.SCOPE_GLOBAL.equals(normalized)) {
            throw new IllegalArgumentException("scope 仅支持 DEPARTMENT 或 GLOBAL");
        }
        return normalized;
    }

    private void validateRequest(CanonicalImportRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("Canonical 导入请求不能为空");
        }
        if (isBlank(request.title()) || isBlank(request.department()) || request.year() == null
                || request.year() < 1900 || request.year() > 2200) {
            throw new IllegalArgumentException("Canonical Document 缺少有效的 title、department 或 year");
        }
        if (request.sections() == null || request.sections().isEmpty()) {
            throw new IllegalArgumentException("Canonical Document 至少需要一个 section");
        }
        for (CanonicalImportRequest.CanonicalSectionRequest section : request.sections()) {
            if (section == null || isBlank(section.section()) || section.facts() == null || section.facts().isEmpty()) {
                throw new IllegalArgumentException("每个 Canonical section 至少需要一个 fact");
            }
            for (CanonicalImportRequest.CanonicalFactRequest fact : section.facts()) {
                if (fact == null || isBlank(fact.text()) || fact.evidenceRefs() == null || fact.evidenceRefs().isEmpty()) {
                    throw new IllegalArgumentException("每个 Canonical fact 至少需要一个 EvidenceRef");
                }
                for (CanonicalImportRequest.EvidenceRefRequest ref : fact.evidenceRefs()) {
                    if (ref == null || ref.sourceDocumentId() == null || ref.sourceDocumentId() <= 0
                            || ref.page() == null || ref.page() <= 0 || isBlank(ref.evidenceText())) {
                        throw new IllegalArgumentException("EvidenceRef 缺少有效的 sourceDocumentId、page 或 evidenceText");
                    }
                }
            }
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private String trimToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }
}
