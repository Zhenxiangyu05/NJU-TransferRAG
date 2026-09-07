package com.yu.transferrag.service;

import com.yu.transferrag.dto.BatchDocumentImportResponse;
import com.yu.transferrag.dto.DocumentImportResponse;
import com.yu.transferrag.dto.DocumentResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentImportServiceTest {

    @Mock
    private DocumentService documentService;

    @Mock
    private ChunkService chunkService;

    @Mock
    private VectorIndexService vectorIndexService;

    private DocumentImportService documentImportService;

    @BeforeEach
    void setUp() {
        documentImportService = new DocumentImportService(
                documentService,
                chunkService,
                vectorIndexService
        );
    }

    @Test
    void shouldImportOneDocumentInOrderAndGenerateTitleFromFileName() {
        MockMultipartFile file = markdownFile("转光电概述.md");
        DocumentResponse savedDocument = documentResponse(
                13L,
                "转光电概述",
                "现代工程学院",
                2026,
                "COMMUNITY"
        );
        when(documentService.uploadDocumentIfAbsent(
                file,
                "转光电概述",
                "现代工程学院",
                2026,
                "COMMUNITY"
        )).thenReturn(new DocumentService.UploadResult(savedDocument, false));
        when(chunkService.createChunks(13L)).thenReturn(6);
        when(vectorIndexService.indexDocument(13L)).thenReturn(6);

        DocumentImportResponse response = documentImportService.importDocument(
                file,
                " ",
                "现代工程学院",
                2026,
                "COMMUNITY"
        );

        assertEquals(13L, response.getDocumentId());
        assertEquals("转光电概述", response.getTitle());
        assertEquals("现代工程学院", response.getDepartment());
        assertEquals(2026, response.getYear());
        assertEquals("COMMUNITY", response.getSourceType());
        assertEquals(6, response.getChunkCount());
        assertEquals(6, response.getIndexedChunks());
        assertEquals("SUCCESS", response.getStatus());

        InOrder inOrder = inOrder(documentService, chunkService, vectorIndexService);
        inOrder.verify(documentService).uploadDocumentIfAbsent(
                file,
                "转光电概述",
                "现代工程学院",
                2026,
                "COMMUNITY"
        );
        inOrder.verify(chunkService).createChunks(13L);
        inOrder.verify(vectorIndexService).indexDocument(13L);
    }

    @Test
    void shouldReturnExistingDocumentAndSkipChunkAndIndexForDuplicateFile() {
        MockMultipartFile file = markdownFile("duplicate.md");
        DocumentResponse existingDocument = documentResponse(
                13L,
                "已存在文档",
                "现代工程学院",
                2026,
                "COMMUNITY"
        );
        when(documentService.uploadDocumentIfAbsent(
                file,
                "duplicate",
                "现代工程学院",
                2026,
                "COMMUNITY"
        )).thenReturn(new DocumentService.UploadResult(existingDocument, true));

        DocumentImportResponse response = documentImportService.importDocument(
                file,
                null,
                "现代工程学院",
                2026,
                "COMMUNITY"
        );

        assertEquals(13L, response.getDocumentId());
        assertEquals("已存在文档", response.getTitle());
        assertEquals(0, response.getChunkCount());
        assertEquals(0, response.getIndexedChunks());
        assertEquals("SUCCESS", response.getStatus());
        assertEquals(true, response.isDuplicate());
        verifyNoInteractions(chunkService, vectorIndexService);
    }

    @Test
    void shouldContinueBatchAfterOneFileFails() {
        MockMultipartFile firstFile = markdownFile("first.md");
        MockMultipartFile failedFile = markdownFile("failed.md");
        MockMultipartFile lastFile = markdownFile("last.md");

        when(documentService.uploadDocumentIfAbsent(
                firstFile, "first", "软件学院", 2026, "COMMUNITY"
        )).thenReturn(new DocumentService.UploadResult(
                documentResponse(1L, "first", "软件学院", 2026, "COMMUNITY"),
                false
        ));
        when(chunkService.createChunks(1L)).thenReturn(2);
        when(vectorIndexService.indexDocument(1L)).thenReturn(2);

        when(documentService.uploadDocumentIfAbsent(
                failedFile, "failed", "软件学院", 2026, "COMMUNITY"
        )).thenThrow(new IllegalArgumentException("测试文件无法保存"));

        when(documentService.uploadDocumentIfAbsent(
                lastFile, "last", "软件学院", 2026, "COMMUNITY"
        )).thenReturn(new DocumentService.UploadResult(
                documentResponse(3L, "last", "软件学院", 2026, "COMMUNITY"),
                false
        ));
        when(chunkService.createChunks(3L)).thenReturn(4);
        when(vectorIndexService.indexDocument(3L)).thenReturn(4);

        BatchDocumentImportResponse response = documentImportService.importBatch(
                java.util.List.of(firstFile, failedFile, lastFile),
                "软件学院",
                2026,
                "COMMUNITY"
        );

        assertEquals(3, response.getTotal());
        assertEquals(2, response.getSuccess());
        assertEquals(1, response.getFailed());
        assertEquals("SUCCESS", response.getResults().get(0).getStatus());
        assertEquals("FAILED", response.getResults().get(1).getStatus());
        assertEquals("测试文件无法保存", response.getResults().get(1).getError());
        assertNull(response.getResults().get(1).getDocumentId());
        assertEquals("SUCCESS", response.getResults().get(2).getStatus());
        assertEquals(3L, response.getResults().get(2).getDocumentId());
        assertEquals(4, response.getResults().get(2).getIndexedChunks());

        verify(vectorIndexService).indexDocument(3L);
    }

    private MockMultipartFile markdownFile(String fileName) {
        return new MockMultipartFile(
                "files",
                fileName,
                "text/markdown",
                "测试内容".getBytes(java.nio.charset.StandardCharsets.UTF_8)
        );
    }

    private DocumentResponse documentResponse(Long id,
                                              String title,
                                              String department,
                                              Integer year,
                                              String sourceType) {
        DocumentResponse response = new DocumentResponse();
        response.setId(id);
        response.setTitle(title);
        response.setDepartment(department);
        response.setYear(year);
        response.setSourceType(sourceType);
        return response;
    }
}
