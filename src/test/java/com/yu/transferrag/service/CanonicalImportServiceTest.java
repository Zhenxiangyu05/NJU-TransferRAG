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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CanonicalImportServiceTest {

    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private ChunkRepository chunkRepository;
    @Mock
    private EvidenceRefRepository evidenceRefRepository;
    @Mock
    private VectorIndexService vectorIndexService;

    private CanonicalImportService canonicalImportService;

    @BeforeEach
    void setUp() {
        canonicalImportService = new CanonicalImportService(
                documentRepository, chunkRepository, evidenceRefRepository, vectorIndexService
        );
    }

    @Test
    void shouldTreatLegacyDocumentWithoutRoleAsEvidence() {
        Document legacyEvidence = evidenceDocument(12L, null);
        stubSuccessfulImport(legacyEvidence);

        CanonicalImportResponse response = canonicalImportService.importCanonical(validRequest());

        assertEquals(DocumentRole.CANONICAL, response.documentRole());
        assertEquals(Document.SOURCE_TYPE_CURATED, response.sourceType());
        assertEquals(2, response.chunkCount());
        assertEquals(4, response.evidenceRefCount());
    }

    @Test
    void shouldCreateSemanticChunksAndFactLevelEvidenceReferences() {
        Document evidence = evidenceDocument(12L, DocumentRole.EVIDENCE);
        stubSuccessfulImport(evidence);

        canonicalImportService.importCanonical(validRequest());

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(documentCaptor.capture());
        assertEquals(DocumentRole.CANONICAL, documentCaptor.getValue().getDocumentRole());
        assertEquals(Document.SOURCE_TYPE_CURATED, documentCaptor.getValue().getSourceType());
        assertEquals("UPLOADED", documentCaptor.getValue().getStatus());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Chunk>> chunksCaptor = ArgumentCaptor.forClass(List.class);
        verify(chunkRepository).saveAll(chunksCaptor.capture());
        List<Chunk> chunks = chunksCaptor.getValue();
        assertEquals("申请条件", chunks.get(0).getSection());
        assertEquals("主题：申请条件\n[F0] GPA 不低于 3.0\n[F1] 需参加面试", chunks.get(0).getContent());
        assertEquals("材料要求", chunks.get(1).getSection());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<EvidenceRef>> refsCaptor = ArgumentCaptor.forClass(List.class);
        verify(evidenceRefRepository).saveAll(refsCaptor.capture());
        List<EvidenceRef> refs = refsCaptor.getValue();
        assertEquals(4, refs.size());
        assertEquals(0, refs.get(0).getFactIndex());
        assertEquals(1, refs.get(1).getFactIndex());
        assertEquals(1, refs.get(2).getFactIndex());
        assertEquals(3, refs.get(0).getSourcePage());
        assertEquals(4, refs.get(1).getSourcePage());
        assertEquals(12L, refs.get(2).getEvidenceDocument().getId());
        assertEquals(0, refs.get(3).getFactIndex());
    }

    @Test
    void shouldRejectMissingEvidenceDocumentBeforePersistingCanonicalData() {
        when(documentRepository.findAllById(any())).thenReturn(List.of());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> canonicalImportService.importCanonical(validRequest())
        );

        assertEquals(true, error.getMessage().contains("不存在"));
        verify(documentRepository, never()).save(any(Document.class));
        verifyNoInteractions(chunkRepository, evidenceRefRepository, vectorIndexService);
    }

    @Test
    void shouldRejectCanonicalDocumentAsEvidenceBeforePersistingCanonicalData() {
        when(documentRepository.findAllById(any())).thenReturn(List.of(
                evidenceDocument(12L, DocumentRole.CANONICAL)
        ));

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> canonicalImportService.importCanonical(validRequest())
        );

        assertEquals(true, error.getMessage().contains("只能引用 EVIDENCE"));
        verify(documentRepository, never()).save(any(Document.class));
        verifyNoInteractions(chunkRepository, evidenceRefRepository, vectorIndexService);
    }

    @Test
    void shouldRejectInvalidFactBeforePersistingAnything() {
        CanonicalImportRequest invalid = new CanonicalImportRequest(
                "软件学院规则卡", "软件学院", 2026, "DEPARTMENT", List.of(
                new CanonicalImportRequest.CanonicalSectionRequest(
                        "申请条件", null, null, null, null, List.of(
                        new CanonicalImportRequest.CanonicalFactRequest("GPA 不低于 3.0", List.of())
                ))
        ));

        assertThrows(IllegalArgumentException.class, () -> canonicalImportService.importCanonical(invalid));

        verifyNoInteractions(documentRepository, chunkRepository, evidenceRefRepository, vectorIndexService);
    }

    @Test
    void shouldCompensateOnlyNewCanonicalDocumentPointsWhenIndexingFails() {
        Document evidence = evidenceDocument(12L, DocumentRole.EVIDENCE);
        stubSuccessfulImport(evidence);
        when(vectorIndexService.indexDocument(90L)).thenThrow(new IllegalStateException("Qdrant unavailable"));

        assertThrows(IllegalStateException.class, () -> canonicalImportService.importCanonical(validRequest()));

        verify(vectorIndexService).removeDocumentIndex(90L);
    }

    private void stubSuccessfulImport(Document evidenceDocument) {
        when(documentRepository.findAllById(any())).thenReturn(List.of(evidenceDocument));
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document canonical = invocation.getArgument(0);
            canonical.setId(90L);
            return canonical;
        });
        when(chunkRepository.saveAll(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Chunk> chunks = invocation.getArgument(0);
            for (int index = 0; index < chunks.size(); index++) {
                chunks.get(index).setId(900L + index);
            }
            return chunks;
        });
        when(vectorIndexService.indexDocument(90L)).thenReturn(2);
    }

    private Document evidenceDocument(Long id, DocumentRole role) {
        Document document = new Document();
        document.setId(id);
        document.setTitle("2026 转专业原始通知");
        document.setDepartment("软件学院");
        document.setYear(2026);
        document.setSourceType("OFFICIAL");
        if (role != null) {
            document.setDocumentRole(role);
        }
        return document;
    }

    private CanonicalImportRequest validRequest() {
        return new CanonicalImportRequest(
                "软件学院转专业知识卡",
                "软件学院",
                2026,
                "DEPARTMENT",
                List.of(
                        new CanonicalImportRequest.CanonicalSectionRequest(
                                "申请条件", 2026, 2025, "软件学院", "软件工程",
                                List.of(
                                        new CanonicalImportRequest.CanonicalFactRequest(
                                                "GPA 不低于 3.0",
                                                List.of(new CanonicalImportRequest.EvidenceRefRequest(
                                                        12L, 3, "原始通知明确要求 GPA 不低于 3.0。"
                                                ))
                                        ),
                                        new CanonicalImportRequest.CanonicalFactRequest(
                                                "需参加面试",
                                                List.of(
                                                        new CanonicalImportRequest.EvidenceRefRequest(
                                                                12L, 4, "原始通知要求参加面试。"
                                                        ),
                                                        new CanonicalImportRequest.EvidenceRefRequest(
                                                                12L, 5, "面试安排见原始通知。"
                                                        )
                                                )
                                        )
                                )
                        ),
                        new CanonicalImportRequest.CanonicalSectionRequest(
                                "材料要求", 2026, 2025, "软件学院", "软件工程",
                                List.of(new CanonicalImportRequest.CanonicalFactRequest(
                                        "需提交成绩单",
                                        List.of(new CanonicalImportRequest.EvidenceRefRequest(
                                                12L, 6, "原始通知列出成绩单。"
                                        ))
                                ))
                        )
                )
        );
    }
}
