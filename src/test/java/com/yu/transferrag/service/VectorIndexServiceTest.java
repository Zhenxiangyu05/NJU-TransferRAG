package com.yu.transferrag.service;

import com.yu.transferrag.entity.Chunk;
import com.yu.transferrag.repository.ChunkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VectorIndexServiceTest {

    @Mock
    private ChunkRepository chunkRepository;

    @Mock
    private VectorStore vectorStore;

    private VectorIndexService vectorIndexService;

    @BeforeEach
    void setUp() {
        vectorIndexService = new VectorIndexService(chunkRepository, vectorStore);
    }

    @Test
    void shouldAddDocumentMetadataToVectorDocument() {
        com.yu.transferrag.entity.Document sourceDocument = new com.yu.transferrag.entity.Document();
        sourceDocument.setId(13L);
        sourceDocument.setDepartment("现代工程学院");
        sourceDocument.setYear(2026);
        sourceDocument.setSourceType("COMMUNITY");

        Chunk chunk = new Chunk();
        chunk.setId(101L);
        chunk.setDocument(sourceDocument);
        chunk.setContent("光电实验班的转专业条件");
        chunk.setChunkIndex(0);
        when(chunkRepository.findByDocument_IdOrderByChunkIndexAsc(13L)).thenReturn(List.of(chunk));

        int indexedChunks = vectorIndexService.indexDocument(13L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<org.springframework.ai.document.Document>> documentsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(documentsCaptor.capture());

        Map<String, Object> metadata = documentsCaptor.getValue().getFirst().getMetadata();
        assertEquals(1, indexedChunks);
        assertEquals("101", metadata.get("chunkId"));
        assertEquals("13", metadata.get("documentId"));
        assertEquals(0, metadata.get("chunkIndex"));
        assertEquals("现代工程学院", metadata.get("department"));
        assertEquals(2026, metadata.get("year"));
        assertEquals("COMMUNITY", metadata.get("sourceType"));
        assertEquals("DEPARTMENT", metadata.get("scope"));
        assertEquals(2026, metadata.get("effectiveYear"));
        assertNull(metadata.get("policyYear"));
        assertNull(metadata.get("chunkDepartment"));
        assertNull(metadata.get("major"));
    }

    @Test
    void shouldAddGlobalScopeToOfficialDocumentMetadata() {
        com.yu.transferrag.entity.Document sourceDocument = new com.yu.transferrag.entity.Document();
        sourceDocument.setId(6L);
        sourceDocument.setDepartment("本科生院");
        sourceDocument.setYear(2026);
        sourceDocument.setSourceType("OFFICIAL");
        sourceDocument.setScope(com.yu.transferrag.entity.Document.SCOPE_GLOBAL);

        Chunk chunk = new Chunk();
        chunk.setId(60L);
        chunk.setDocument(sourceDocument);
        chunk.setContent("汉语言文学专业准入条件");
        chunk.setChunkIndex(0);
        chunk.setPolicyYear(2026);
        chunk.setCohortYear(2025);
        chunk.setDepartment("文学院");
        chunk.setMajor("汉语言文学");
        when(chunkRepository.findByDocument_IdOrderByChunkIndexAsc(6L)).thenReturn(List.of(chunk));

        vectorIndexService.indexDocument(6L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<org.springframework.ai.document.Document>> documentsCaptor =
                ArgumentCaptor.forClass(List.class);
        verify(vectorStore).add(documentsCaptor.capture());

        Map<String, Object> metadata = documentsCaptor.getValue().getFirst().getMetadata();
        assertEquals("GLOBAL", metadata.get("scope"));
        assertEquals("本科生院", metadata.get("department"));
        assertEquals(2026, metadata.get("year"));
        assertEquals(2026, metadata.get("policyYear"));
        assertEquals(2025, metadata.get("cohortYear"));
        assertEquals(2026, metadata.get("effectiveYear"));
        assertEquals("文学院", metadata.get("chunkDepartment"));
        assertEquals("汉语言文学", metadata.get("major"));
    }
}
