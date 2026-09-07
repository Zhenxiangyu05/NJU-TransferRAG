package com.yu.transferrag.service;

import com.yu.transferrag.entity.Document;
import com.yu.transferrag.repository.DocumentRepository;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.tika.exception.TikaException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PdfParserServiceTest {

    @Mock
    private DocumentRepository documentRepository;

    @TempDir
    private Path tempDirectory;

    private Path pdfPath;
    private Document document;

    @BeforeEach
    void setUp() throws IOException {
        pdfPath = tempDirectory.resolve("test.pdf");
        try (PDDocument pdfDocument = new PDDocument()) {
            pdfDocument.addPage(new PDPage());
            pdfDocument.save(pdfPath.toFile());
        }

        document = new Document();
        document.setId(5L);
        document.setFilePath(pdfPath.toString());
        when(documentRepository.findById(5L)).thenReturn(Optional.of(document));
    }

    @Test
    void shouldNotCallTikaWhenPdfBoxReturnsText() {
        StubPdfParserService parserService = new StubPdfParserService(documentRepository);
        parserService.pdfBoxText = "PDFBox 提取出的正文";

        String text = parserService.extractText(5L);

        assertEquals("PDFBox 提取出的正文", text);
        assertEquals(1, parserService.pdfBoxCallCount);
        assertEquals(0, parserService.tikaCallCount);
        assertEquals(0, parserService.ocrCallCount);
    }

    @Test
    void shouldCallTikaWhenPdfBoxReturnsBlankText() {
        StubPdfParserService parserService = new StubPdfParserService(documentRepository);
        parserService.pdfBoxText = "\r\n\t ";
        parserService.tikaText = "Tika 正文";

        parserService.extractText(5L);

        assertEquals(1, parserService.pdfBoxCallCount);
        assertEquals(1, parserService.tikaCallCount);
        assertEquals(0, parserService.ocrCallCount);
    }

    @Test
    void shouldReturnTextWhenTikaFallbackSucceeds() {
        StubPdfParserService parserService = new StubPdfParserService(documentRepository);
        parserService.pdfBoxText = null;
        parserService.tikaText = "Tika 成功提取的正文";

        String text = parserService.extractText(5L);

        assertEquals("Tika 成功提取的正文", text);
        assertEquals(0, parserService.ocrCallCount);
    }

    @Test
    void shouldCallOcrWhenPdfBoxAndTikaBothReturnBlankText() {
        StubPdfParserService parserService = new StubPdfParserService(documentRepository);
        parserService.pdfBoxText = "\r\n";
        parserService.tikaText = " \n\t";
        parserService.ocrText = "OCR 正文";

        parserService.extractText(5L);

        assertEquals(1, parserService.tikaCallCount);
        assertEquals(1, parserService.ocrCallCount);
    }

    @Test
    void shouldReturnTextWhenOcrFallbackSucceeds() {
        StubPdfParserService parserService = new StubPdfParserService(documentRepository);
        parserService.pdfBoxText = "";
        parserService.tikaText = "\r\n";
        parserService.ocrText = "OCR 成功提取的中文正文";

        String text = parserService.extractText(5L);

        assertEquals("OCR 成功提取的中文正文", text);
    }

    @Test
    void shouldThrowFinalErrorWhenOcrAlsoReturnsBlankText() {
        StubPdfParserService parserService = new StubPdfParserService(documentRepository);
        parserService.pdfBoxText = "";
        parserService.tikaText = "\r\n";
        parserService.ocrText = " \n\t";

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> parserService.extractText(5L)
        );

        assertEquals("PDFBox、Tika 和 OCR 均未提取到文本", exception.getMessage());
        assertEquals(1, parserService.ocrCallCount);
    }

    @Test
    void shouldExtractTextWithRealTikaPdfParser() throws IOException, TikaException {
        Path textPdfPath = tempDirectory.resolve("tika-text.pdf");
        try (PDDocument pdfDocument = new PDDocument()) {
            PDPage page = new PDPage();
            pdfDocument.addPage(page);
            try (PDPageContentStream contentStream = new PDPageContentStream(pdfDocument, page)) {
                contentStream.beginText();
                contentStream.setFont(
                        new PDType1Font(Standard14Fonts.FontName.HELVETICA),
                        12
                );
                contentStream.newLineAtOffset(72, 720);
                contentStream.showText("Tika fallback content");
                contentStream.endText();
            }
            pdfDocument.save(textPdfPath.toFile());
        }

        document.setFilePath(textPdfPath.toString());

        PdfParserService parserService = new PdfParserService(
                documentRepository,
                "tesseract",
                "",
                "chi_sim+eng",
                300
        ) {
            @Override
            String extractWithPdfBox(PDDocument pdfDocument) {
                return "";
            }
        };

        String text = parserService.extractText(5L);

        assertTrue(text.contains("Tika fallback content"));
    }

    private static class StubPdfParserService extends PdfParserService {

        private String pdfBoxText;
        private String tikaText;
        private String ocrText;
        private int pdfBoxCallCount;
        private int tikaCallCount;
        private int ocrCallCount;

        private StubPdfParserService(DocumentRepository documentRepository) {
            super(documentRepository, "tesseract", "", "chi_sim+eng", 300);
        }

        @Override
        String extractWithPdfBox(PDDocument pdfDocument) {
            pdfBoxCallCount++;
            return pdfBoxText;
        }

        @Override
        String extractWithTika(Path filePath) throws IOException, TikaException {
            tikaCallCount++;
            return tikaText;
        }

        @Override
        String extractWithOcr(PDDocument pdfDocument, Long documentId) {
            ocrCallCount++;
            return ocrText;
        }
    }
}
