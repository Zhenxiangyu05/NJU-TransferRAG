package com.yu.transferrag.service;

import com.yu.transferrag.entity.Document;
import com.yu.transferrag.repository.DocumentRepository;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.pdf.PDFParser;
import org.apache.tika.parser.pdf.PDFParserConfig;
import org.apache.tika.sax.BodyContentHandler;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.xml.sax.SAXException;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class PdfParserService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PdfParserService.class);
    private static final int PREVIEW_LENGTH = 300;
    private static final long OCR_PAGE_TIMEOUT_SECONDS = 120;

    private final DocumentRepository documentRepository;
    private final String tesseractCommand;
    private final String tessdataDirectory;
    private final String ocrLanguage;
    private final int ocrDpi;

    public PdfParserService(
            DocumentRepository documentRepository,
            @Value("${app.ocr.tesseract-command:tesseract}") String tesseractCommand,
            @Value("${app.ocr.tessdata-dir:}") String tessdataDirectory,
            @Value("${app.ocr.language:chi_sim+eng}") String ocrLanguage,
            @Value("${app.ocr.dpi:300}") int ocrDpi) {
        this.documentRepository = documentRepository;
        this.tesseractCommand = tesseractCommand;
        this.tessdataDirectory = tessdataDirectory;
        this.ocrLanguage = ocrLanguage;
        this.ocrDpi = ocrDpi;
    }

    public String extractText(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("文档不存在"));

        String documentFilePath = document.getFilePath();
        if (documentFilePath == null || documentFilePath.isBlank()) {
            throw new IllegalStateException("文档文件路径不存在");
        }

        Path filePath = Path.of(documentFilePath);
        if (!Files.exists(filePath) || !Files.isRegularFile(filePath)) {
            throw new IllegalStateException("PDF 文件不存在或不是普通文件");
        }

        try (PDDocument pdfDocument = Loader.loadPDF(filePath.toFile())) {
            String text;
            try {
                text = extractWithPdfBox(pdfDocument);
            } catch (IOException e) {
                LOGGER.error(
                        "PDFTextStripper.getText() 失败: documentId={}, exceptionType={}, message={}",
                        documentId,
                        e.getClass().getName(),
                        safeLogText(e.getMessage(), PREVIEW_LENGTH),
                        e
                );
                throw new IllegalStateException("PDF 无法读取", e);
            } catch (RuntimeException e) {
                LOGGER.error(
                        "PDFTextStripper.getText() 失败: documentId={}, exceptionType={}, message={}",
                        documentId,
                        e.getClass().getName(),
                        safeLogText(e.getMessage(), PREVIEW_LENGTH),
                        e
                );
                throw e;
            }

            int textLength = text == null ? -1 : text.length();
            int trimmedLength = text == null ? -1 : text.trim().length();
            LOGGER.info(
                    "PDF 文本提取结果: documentId={}, pageCount={}, textLength={}, trimmedLength={}",
                    documentId,
                    pdfDocument.getNumberOfPages(),
                    textLength,
                    trimmedLength
            );
            LOGGER.info(
                    "PDF 文本提取预览: documentId={}, first300={}",
                    documentId,
                    safeLogText(text, PREVIEW_LENGTH)
            );

            if (text != null && !text.isEmpty() && text.trim().isEmpty()) {
                LOGGER.warn(
                        "PDFTextStripper 返回了非空字符串，但 trim 后为空: documentId={}, textLength={}",
                        documentId,
                        text.length()
                );
            }

            if (text == null || text.isBlank()) {
                return extractWithTikaFallback(documentId, filePath, pdfDocument);
            }
            return text;
        } catch (IOException e) {
            LOGGER.error(
                    "PDF 加载或关闭失败: documentId={}, exceptionType={}, message={}",
                    documentId,
                    e.getClass().getName(),
                    safeLogText(e.getMessage(), PREVIEW_LENGTH),
                    e
            );
            throw new IllegalStateException("PDF 无法读取", e);
        }
    }

    String extractWithPdfBox(PDDocument pdfDocument) throws IOException {
        return new PDFTextStripper().getText(pdfDocument);
    }

    String extractWithTika(Path filePath) throws IOException, TikaException {
        PDFParser parser = new PDFParser();
        PDFParserConfig parserConfig = new PDFParserConfig();
        parserConfig.setOcrStrategy(PDFParserConfig.OCR_STRATEGY.NO_OCR);
        parser.setPDFParserConfig(parserConfig);

        ParseContext parseContext = new ParseContext();
        parseContext.set(PDFParserConfig.class, parserConfig);
        BodyContentHandler contentHandler = new BodyContentHandler(-1);

        try (InputStream inputStream = Files.newInputStream(filePath)) {
            parser.parse(
                    inputStream,
                    contentHandler,
                    new Metadata(),
                    parseContext
            );
        } catch (SAXException e) {
            throw new TikaException("Tika 处理 PDF 文本失败", e);
        }
        return contentHandler.toString();
    }

    private String extractWithTikaFallback(Long documentId,
                                           Path filePath,
                                           PDDocument pdfDocument) {
        LOGGER.warn(
                "PDFBox extraction blank, fallback to Tika: documentId={}",
                documentId
        );

        String tikaText;
        try {
            tikaText = extractWithTika(filePath);
        } catch (IOException | TikaException | RuntimeException e) {
            LOGGER.error(
                    "Tika PDF 文本提取失败: documentId={}, exceptionType={}, message={}",
                    documentId,
                    e.getClass().getName(),
                    safeLogText(e.getMessage(), PREVIEW_LENGTH),
                    e
            );
            throw new IllegalStateException("Tika 无法提取 PDF 文本", e);
        }

        int textLength = tikaText == null ? -1 : tikaText.length();
        int trimmedLength = tikaText == null ? -1 : tikaText.trim().length();
        LOGGER.info(
                "Tika PDF 文本提取结果: documentId={}, textLength={}, trimmedLength={}",
                documentId,
                textLength,
                trimmedLength
        );
        LOGGER.info(
                "Tika PDF 文本提取预览: documentId={}, first300={}",
                documentId,
                safeLogText(tikaText, PREVIEW_LENGTH)
        );

        if (tikaText != null && !tikaText.isEmpty() && tikaText.trim().isEmpty()) {
            LOGGER.warn(
                    "Tika 返回了非空字符串，但 trim 后为空: documentId={}, textLength={}",
                    documentId,
                    tikaText.length()
            );
        }

        if (tikaText == null || tikaText.isBlank()) {
            return extractWithOcrFallback(documentId, pdfDocument);
        }
        return tikaText;
    }

    private String extractWithOcrFallback(Long documentId, PDDocument pdfDocument) {
        LOGGER.warn(
                "OCR fallback triggered: documentId={}, pageCount={}",
                documentId,
                pdfDocument.getNumberOfPages()
        );

        String ocrText;
        try {
            ocrText = extractWithOcr(pdfDocument, documentId);
        } catch (RuntimeException e) {
            LOGGER.error(
                    "OCR PDF 文本提取失败: documentId={}, exceptionType={}, message={}",
                    documentId,
                    e.getClass().getName(),
                    safeLogText(e.getMessage(), PREVIEW_LENGTH),
                    e
            );
            throw e;
        }

        int textLength = ocrText == null ? -1 : ocrText.length();
        int trimmedLength = ocrText == null ? -1 : ocrText.trim().length();
        LOGGER.info(
                "OCR PDF 文本提取结果: documentId={}, textLength={}, trimmedLength={}",
                documentId,
                textLength,
                trimmedLength
        );
        LOGGER.info(
                "OCR PDF 文本提取预览: documentId={}, first300={}",
                documentId,
                safeLogText(ocrText, PREVIEW_LENGTH)
        );

        if (ocrText == null || ocrText.isBlank()) {
            throw new IllegalStateException("PDFBox、Tika 和 OCR 均未提取到文本");
        }
        return ocrText;
    }

    String extractWithOcr(PDDocument pdfDocument, Long documentId) {
        validateOcrConfiguration();

        Path temporaryDirectory = null;
        try {
            temporaryDirectory = Files.createTempDirectory(
                    "transfer-rag-ocr-" + documentId + "-"
            );
            PDFRenderer renderer = new PDFRenderer(pdfDocument);
            StringBuilder extractedText = new StringBuilder();
            RuntimeException firstPageFailure = null;

            for (int pageIndex = 0; pageIndex < pdfDocument.getNumberOfPages(); pageIndex++) {
                int pageNumber = pageIndex + 1;
                try {
                    String pageText = extractPageWithOcr(
                            renderer,
                            pageIndex,
                            temporaryDirectory
                    );
                    int pageTextLength = pageText == null ? -1 : pageText.length();
                    int pageTrimmedLength = pageText == null
                            ? -1
                            : pageText.trim().length();
                    LOGGER.info(
                            "OCR page result: documentId={}, page={}/{}, success=true, "
                                    + "textLength={}, trimmedLength={}",
                            documentId,
                            pageNumber,
                            pdfDocument.getNumberOfPages(),
                            pageTextLength,
                            pageTrimmedLength
                    );

                    if (pageText != null && !pageText.isBlank()) {
                        if (!extractedText.isEmpty()) {
                            extractedText.append(System.lineSeparator());
                        }
                        extractedText.append(pageText.trim());
                    }
                } catch (IOException | RuntimeException e) {
                    if (firstPageFailure == null) {
                        firstPageFailure = e instanceof RuntimeException runtimeException
                                ? runtimeException
                                : new IllegalStateException(e);
                    }
                    LOGGER.error(
                            "OCR page result: documentId={}, page={}/{}, success=false, "
                                    + "exceptionType={}, message={}",
                            documentId,
                            pageNumber,
                            pdfDocument.getNumberOfPages(),
                            e.getClass().getName(),
                            safeLogText(e.getMessage(), PREVIEW_LENGTH),
                            e
                    );
                }
            }

            if (extractedText.isEmpty() && firstPageFailure != null) {
                throw new IllegalStateException("OCR 所有页面处理失败", firstPageFailure);
            }
            return extractedText.toString();
        } catch (IOException e) {
            throw new IllegalStateException("创建 OCR 临时目录失败", e);
        } finally {
            deleteTemporaryDirectory(temporaryDirectory, documentId);
        }
    }

    private String extractPageWithOcr(PDFRenderer renderer,
                                      int pageIndex,
                                      Path temporaryDirectory) throws IOException {
        String pageName = "page-" + String.format("%04d", pageIndex + 1);
        Path imagePath = temporaryDirectory.resolve(pageName + ".png");
        Path outputBasePath = temporaryDirectory.resolve(pageName + "-ocr");
        Path outputTextPath = temporaryDirectory.resolve(pageName + "-ocr.txt");
        Path diagnosticPath = temporaryDirectory.resolve(pageName + "-tesseract.log");

        BufferedImage pageImage = renderer.renderImageWithDPI(
                pageIndex,
                ocrDpi,
                ImageType.RGB
        );
        try {
            if (!ImageIO.write(pageImage, "png", imagePath.toFile())) {
                throw new IOException("无法将 PDF 页面写入 PNG 图片");
            }
        } finally {
            pageImage.flush();
        }

        List<String> command = buildTesseractCommand(imagePath, outputBasePath);
        Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .redirectOutput(diagnosticPath.toFile())
                .start();

        boolean finished;
        try {
            finished = process.waitFor(OCR_PAGE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            process.destroyForcibly();
            throw new IllegalStateException("等待 Tesseract OCR 时线程被中断", e);
        }

        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException(
                    "Tesseract OCR 单页处理超时（" + OCR_PAGE_TIMEOUT_SECONDS + " 秒）"
            );
        }

        String diagnostics = Files.exists(diagnosticPath)
                ? Files.readString(diagnosticPath, StandardCharsets.UTF_8)
                : "";
        if (process.exitValue() != 0) {
            throw new IllegalStateException(
                    "Tesseract OCR 退出码为 " + process.exitValue()
                            + ": " + safeLogText(diagnostics, PREVIEW_LENGTH)
            );
        }

        if (!Files.exists(outputTextPath)) {
            throw new IllegalStateException("Tesseract OCR 未生成文本结果文件");
        }
        return Files.readString(outputTextPath, StandardCharsets.UTF_8);
    }

    private List<String> buildTesseractCommand(Path imagePath, Path outputBasePath) {
        List<String> command = new ArrayList<>();
        command.add(tesseractCommand.trim());
        if (tessdataDirectory != null && !tessdataDirectory.isBlank()) {
            command.add("--tessdata-dir");
            command.add(tessdataDirectory.trim());
        }
        command.add(imagePath.toString());
        command.add(outputBasePath.toString());
        command.add("-l");
        command.add(ocrLanguage.trim());
        return command;
    }

    private void validateOcrConfiguration() {
        if (tesseractCommand == null || tesseractCommand.isBlank()) {
            throw new IllegalStateException("未配置 Tesseract 命令");
        }
        if (ocrLanguage == null || ocrLanguage.isBlank()) {
            throw new IllegalStateException("未配置 OCR 语言");
        }
        if (ocrDpi <= 0) {
            throw new IllegalStateException("OCR DPI 必须大于 0");
        }
    }

    private void deleteTemporaryDirectory(Path temporaryDirectory, Long documentId) {
        if (temporaryDirectory == null || !Files.exists(temporaryDirectory)) {
            return;
        }

        try (var paths = Files.walk(temporaryDirectory)) {
            List<Path> pathsToDelete = paths
                    .sorted(Comparator.reverseOrder())
                    .toList();
            for (Path path : pathsToDelete) {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException e) {
                    LOGGER.warn(
                            "清理 OCR 临时文件失败: documentId={}, path={}, message={}",
                            documentId,
                            path,
                            safeLogText(e.getMessage(), PREVIEW_LENGTH)
                    );
                }
            }
        } catch (IOException e) {
            LOGGER.warn(
                    "遍历 OCR 临时目录失败: documentId={}, message={}",
                    documentId,
                    safeLogText(e.getMessage(), PREVIEW_LENGTH)
            );
        }
    }

    private String safeLogText(String value, int maxCodePoints) {
        if (value == null) {
            return "<null>";
        }

        StringBuilder safeText = new StringBuilder();
        value.codePoints()
                .limit(maxCodePoints)
                .forEach(codePoint -> appendSafeCodePoint(safeText, codePoint));
        return safeText.toString();
    }

    private void appendSafeCodePoint(StringBuilder target, int codePoint) {
        switch (codePoint) {
            case '\\' -> target.append("\\\\");
            case '\r' -> target.append("\\r");
            case '\n' -> target.append("\\n");
            case '\t' -> target.append("\\t");
            default -> {
                if (Character.isISOControl(codePoint)
                        || (Character.isWhitespace(codePoint) && codePoint != ' ')
                        || (codePoint >= Character.MIN_SURROGATE
                        && codePoint <= Character.MAX_SURROGATE)) {
                    target.append(String.format("\\u%04X", codePoint));
                } else {
                    target.appendCodePoint(codePoint);
                }
            }
        }
    }
}
