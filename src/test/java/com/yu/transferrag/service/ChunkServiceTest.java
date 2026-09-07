package com.yu.transferrag.service;

import com.yu.transferrag.entity.Chunk;
import com.yu.transferrag.entity.Document;
import com.yu.transferrag.repository.ChunkRepository;
import com.yu.transferrag.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChunkServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @Mock
    private ChunkRepository chunkRepository;

    @Mock
    private DocumentParserService documentParserService;

    private ChunkService chunkService;

    @BeforeEach
    void setUp() {
        chunkService = new ChunkService(documentRepository, chunkRepository, documentParserService);
    }

    @Test
    void shouldKeepUsingTextChunkerForOrdinaryDocument() {
        Document document = document(10L, "软件学院面试经验", "DEPARTMENT", "COMMUNITY");
        String text = "普通文档内容。".repeat(200);
        when(documentRepository.findById(10L)).thenReturn(Optional.of(document));
        when(documentParserService.extractText(10L)).thenReturn(text);

        int count = chunkService.createChunks(10L);

        List<Chunk> chunks = captureSavedChunks();
        assertTrue(count > 1);
        assertTrue(chunks.stream().allMatch(chunk ->
                chunk.getPolicyYear() == null
                        && chunk.getDepartment() == null
                        && chunk.getMajor() == null
        ));
    }

    @Test
    void shouldCreateStructuredChunksForGlobalOfficialPolicyTable() {
        Document document = document(6L, "全日制本科生专业准入计划", "GLOBAL", "OFFICIAL");
        String text = """
                学院名称 年级 专业名称
                文学院 2025 汉语言文学 11
                汉语言文学申请条件。
                法学院 2025 法学 15
                法学申请条件。
                """;
        when(documentRepository.findById(6L)).thenReturn(Optional.of(document));
        when(documentParserService.extractText(6L)).thenReturn(text);

        int count = chunkService.createChunks(6L);

        List<Chunk> chunks = captureSavedChunks();
        assertEquals(2, count);
        assertEquals("文学院", chunks.get(0).getDepartment());
        assertEquals("汉语言文学", chunks.get(0).getMajor());
        assertEquals(2025, chunks.get(0).getPolicyYear());
        assertEquals("法学院", chunks.get(1).getDepartment());
    }

    @Test
    void shouldFallBackWhenGlobalOfficialTextHasNoReliableRecords() {
        Document document = document(6L, "全日制本科生专业准入计划", "GLOBAL", "OFFICIAL");
        String text = "无法识别表格边界的普通正文。".repeat(100);
        when(documentRepository.findById(6L)).thenReturn(Optional.of(document));
        when(documentParserService.extractText(6L)).thenReturn(text);

        int count = chunkService.createChunks(6L);

        List<Chunk> chunks = captureSavedChunks();
        assertTrue(count > 1);
        assertTrue(chunks.stream().allMatch(chunk -> chunk.getPolicyYear() == null));
    }

    @SuppressWarnings("unchecked")
    private List<Chunk> captureSavedChunks() {
        ArgumentCaptor<List<Chunk>> captor = ArgumentCaptor.forClass(List.class);
        verify(chunkRepository).saveAll(captor.capture());
        return captor.getValue();
    }

    private Document document(Long id, String title, String scope, String sourceType) {
        Document document = new Document();
        document.setId(id);
        document.setTitle(title);
        document.setScope(scope);
        document.setSourceType(sourceType);
        document.setDepartment("本科生院");
        document.setYear(2026);
        return document;
    }
}
