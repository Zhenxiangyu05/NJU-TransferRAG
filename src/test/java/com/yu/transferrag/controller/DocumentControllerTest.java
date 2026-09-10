package com.yu.transferrag.controller;

import com.yu.transferrag.entity.Document;
import com.yu.transferrag.repository.DocumentRepository;
import com.yu.transferrag.service.ChunkService;
import com.yu.transferrag.service.DocumentParserService;
import com.yu.transferrag.service.DocumentService;
import com.yu.transferrag.service.VectorIndexService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentControllerTest {

    @Mock
    private DocumentService documentService;

    @Mock
    private DocumentParserService documentParserService;

    @Mock
    private ChunkService chunkService;

    @Mock
    private VectorIndexService vectorIndexService;

    @Mock
    private DocumentRepository documentRepository;

    @TempDir
    private Path tempDir;

    private DocumentController documentController;

    @BeforeEach
    void setUp() {
        documentController = new DocumentController(
                documentService,
                documentParserService,
                chunkService,
                vectorIndexService,
                documentRepository
        );
    }

    @ParameterizedTest
    @CsvSource({
            "pdf, application/pdf, inline",
            "docx, application/vnd.openxmlformats-officedocument.wordprocessingml.document, attachment",
            "md, text/markdown, inline",
            "txt, text/plain, inline"
    })
    void shouldServeSupportedOriginalFiles(String extension,
                                           String expectedContentType,
                                           String expectedDisposition) throws Exception {
        Path file = tempDir.resolve("source." + extension);
        Files.writeString(file, "document content");
        Document document = document(16L, file, "原始资料." + extension);
        when(documentRepository.findById(16L)).thenReturn(Optional.of(document));

        ResponseEntity<Resource> response = documentController.getOriginalFile(16L);

        assertEquals(200, response.getStatusCode().value());
        assertNotNull(response.getBody());
        assertTrue(response.getBody().exists());
        assertTrue(response.getHeaders().getContentType()
                .isCompatibleWith(MediaType.parseMediaType(expectedContentType)));
        assertTrue(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)
                .startsWith(expectedDisposition));
    }

    @Test
    void shouldReturnNotFoundWhenDocumentDoesNotExist() {
        when(documentRepository.findById(999L)).thenReturn(Optional.empty());

        ResponseEntity<Resource> response = documentController.getOriginalFile(999L);

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void shouldReturnNotFoundWhenFilePathIsMissing() {
        Document document = new Document();
        document.setId(16L);
        when(documentRepository.findById(16L)).thenReturn(Optional.of(document));

        ResponseEntity<Resource> response = documentController.getOriginalFile(16L);

        assertEquals(404, response.getStatusCode().value());
    }

    @Test
    void shouldReturnNotFoundWhenOriginalFileWasDeleted() {
        Path deletedFile = tempDir.resolve("deleted.pdf");
        Document document = document(16L, deletedFile, "deleted.pdf");
        when(documentRepository.findById(16L)).thenReturn(Optional.of(document));

        ResponseEntity<Resource> response = documentController.getOriginalFile(16L);

        assertEquals(404, response.getStatusCode().value());
    }

    private Document document(long id, Path path, String originalFileName) {
        Document document = new Document();
        document.setId(id);
        document.setFilePath(path.toString());
        document.setOriginalFileName(originalFileName);
        return document;
    }
}
