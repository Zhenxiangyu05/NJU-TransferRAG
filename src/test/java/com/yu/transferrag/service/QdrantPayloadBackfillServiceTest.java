package com.yu.transferrag.service;

import com.yu.transferrag.dto.QdrantPayloadBackfillReport;
import com.yu.transferrag.entity.Chunk;
import com.yu.transferrag.entity.Document;
import com.yu.transferrag.entity.DocumentRole;
import com.yu.transferrag.repository.ChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QdrantPayloadBackfillServiceTest {

    @Mock
    private ChunkRepository chunkRepository;
    @Mock
    private QdrantPayloadGateway qdrantPayloadGateway;

    private QdrantPayloadBackfillService service;

    @BeforeEach
    void setUp() {
        service = new QdrantPayloadBackfillService(chunkRepository, qdrantPayloadGateway);
    }

    @Test
    void shouldBackfillLegacyPointWithEvidenceRoleAndSectionWithoutEmbedding() {
        when(qdrantPayloadGateway.scrollAllPoints()).thenReturn(List.of(point(1L, 10L, null, null)));
        when(chunkRepository.findAllByIdIn(Set.of(10L)))
                .thenReturn(List.of(chunk(10L, DocumentRole.EVIDENCE, "申请条件")));

        QdrantPayloadBackfillReport report = service.backfill(false);

        assertEquals(1, report.requiresUpdate());
        assertEquals(1, report.updatedPoints());
        ArgumentCaptor<Map<String, Object>> payload = ArgumentCaptor.forClass(Map.class);
        verify(qdrantPayloadGateway).setPayload(eq(List.of(1L)), payload.capture());
        assertEquals("EVIDENCE", payload.getValue().get("documentRole"));
        assertEquals("申请条件", payload.getValue().get("section"));
    }

    @Test
    void shouldSkipEvidencePointWhenPayloadIsAlreadyCorrect() {
        when(qdrantPayloadGateway.scrollAllPoints())
                .thenReturn(List.of(point(1L, 10L, "EVIDENCE", "申请条件")));
        when(chunkRepository.findAllByIdIn(Set.of(10L)))
                .thenReturn(List.of(chunk(10L, DocumentRole.EVIDENCE, "申请条件")));

        QdrantPayloadBackfillReport report = service.backfill(false);

        assertEquals(1, report.alreadyCorrect());
        assertEquals(0, report.requiresUpdate());
        verify(qdrantPayloadGateway, never()).setPayload(any(), any());
    }

    @Test
    void shouldNeverOverwriteCanonicalPayloadWithEvidence() {
        when(qdrantPayloadGateway.scrollAllPoints())
                .thenReturn(List.of(point(1L, 10L, "CANONICAL", "旧 section")));
        when(chunkRepository.findAllByIdIn(Set.of(10L)))
                .thenReturn(List.of(chunk(10L, DocumentRole.EVIDENCE, "申请条件")));

        QdrantPayloadBackfillReport report = service.backfill(false);

        assertEquals(1, report.canonicalPreserved());
        assertEquals(0, report.requiresUpdate());
        verify(qdrantPayloadGateway, never()).setPayload(any(), any());
    }

    @Test
    void shouldWarnForUnknownChunkWithoutTouchingPoint() {
        when(qdrantPayloadGateway.scrollAllPoints()).thenReturn(List.of(point(1L, 999L, 77L, null, null)));
        when(chunkRepository.findAllByIdIn(Set.of(999L))).thenReturn(List.of());

        QdrantPayloadBackfillReport report = service.backfill(true);

        assertEquals(1, report.missingChunks());
        assertTrue(report.warnings().getFirst().contains("pointId=1 documentId=77"));
        verify(qdrantPayloadGateway, never()).setPayload(any(), any());
    }

    @Test
    void shouldBeIdempotentOnSecondRun() {
        when(qdrantPayloadGateway.scrollAllPoints())
                .thenReturn(List.of(point(1L, 10L, null, null)))
                .thenReturn(List.of(point(1L, 10L, "EVIDENCE", "申请条件")));
        when(chunkRepository.findAllByIdIn(Set.of(10L)))
                .thenReturn(List.of(chunk(10L, DocumentRole.EVIDENCE, "申请条件")));

        QdrantPayloadBackfillReport first = service.backfill(false);
        QdrantPayloadBackfillReport second = service.backfill(false);

        assertEquals(1, first.requiresUpdate());
        assertEquals(0, second.requiresUpdate());
        assertEquals(1, second.alreadyCorrect());
        verify(qdrantPayloadGateway).setPayload(any(), any());
    }

    private QdrantPayloadGateway.QdrantPoint point(Long id, Long chunkId,
                                                    String role, String section) {
        return point(id, chunkId, 1L, role, section);
    }

    private QdrantPayloadGateway.QdrantPoint point(Long id, Long chunkId, Long documentId,
                                                    String role, String section) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("chunkId", chunkId.toString());
        payload.put("documentId", documentId.toString());
        if (role != null) {
            payload.put("documentRole", role);
        }
        if (section != null) {
            payload.put("section", section);
        }
        return new QdrantPayloadGateway.QdrantPoint(id, payload);
    }

    private Chunk chunk(Long id, DocumentRole role, String section) {
        Document document = new Document();
        document.setId(1L);
        document.setDocumentRole(role);
        Chunk chunk = new Chunk();
        chunk.setId(id);
        chunk.setDocument(document);
        chunk.setSection(section);
        return chunk;
    }
}
