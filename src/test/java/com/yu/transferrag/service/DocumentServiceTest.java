package com.yu.transferrag.service;

import com.yu.transferrag.entity.Document;
import com.yu.transferrag.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @TempDir
    private Path uploadDir;

    private DocumentService documentService;

    @BeforeEach
    void setUp() {
        documentService = new DocumentService(documentRepository, uploadDir.toString());
    }

    @Test
    void shouldGenerateSameSha256ForSameContent() {
        MockMultipartFile first = file("first.md", "相同内容");
        MockMultipartFile second = file("second.md", "相同内容");

        String firstHash = documentService.calculateSha256(first);
        String secondHash = documentService.calculateSha256(second);

        assertEquals(firstHash, secondHash);
        assertEquals(64, firstHash.length());
    }

    @Test
    void shouldReturnExistingDocumentWithoutSavingFileAgain() throws Exception {
        Document existingDocument = document(13L, "已存在文档");
        when(documentRepository.findByFileHash(anyString()))
                .thenReturn(Optional.of(existingDocument));

        DocumentService.UploadResult result = documentService.uploadDocumentIfAbsent(
                file("duplicate.md", "重复内容"),
                "新标题",
                "软件学院",
                2026,
                "COMMUNITY"
        );

        assertTrue(result.duplicate());
        assertEquals(13L, result.document().getId());
        assertEquals("已存在文档", result.document().getTitle());
        verify(documentRepository, never()).save(any(Document.class));
        try (var uploadedFiles = Files.list(uploadDir)) {
            assertEquals(0, uploadedFiles.count());
        }
    }

    @Test
    void shouldSaveHashForNewDocument() {
        MockMultipartFile file = file("new.md", "全新内容");
        when(documentRepository.findByFileHash(anyString())).thenReturn(Optional.empty());
        when(documentRepository.save(any(Document.class))).thenAnswer(invocation -> {
            Document document = invocation.getArgument(0);
            document.setId(20L);
            return document;
        });

        DocumentService.UploadResult result = documentService.uploadDocumentIfAbsent(
                file,
                "全新文档",
                "软件学院",
                2026,
                "OFFICIAL"
        );

        ArgumentCaptor<Document> documentCaptor = ArgumentCaptor.forClass(Document.class);
        verify(documentRepository).save(documentCaptor.capture());
        Document savedDocument = documentCaptor.getValue();

        assertFalse(result.duplicate());
        assertEquals(20L, result.document().getId());
        assertEquals(documentService.calculateSha256(file), savedDocument.getFileHash());
        assertTrue(Files.isRegularFile(Path.of(savedDocument.getFilePath())));
    }

    private MockMultipartFile file(String fileName, String content) {
        return new MockMultipartFile(
                "file",
                fileName,
                "text/markdown",
                content.getBytes(StandardCharsets.UTF_8)
        );
    }

    private Document document(Long id, String title) {
        Document document = new Document();
        document.setId(id);
        document.setTitle(title);
        document.setDepartment("软件学院");
        document.setYear(2026);
        document.setSourceType("COMMUNITY");
        return document;
    }
}
