package com.yu.transferrag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yu.transferrag.entity.Document;
import com.yu.transferrag.entity.DocumentRole;
import com.yu.transferrag.repository.DocumentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PublicSourceServiceTest {
    @TempDir Path temp;
    Path uploads;
    Path manifest;
    DocumentRepository repository;
    PublicSourceService service;

    @BeforeEach
    void setup() throws Exception {
        uploads = Files.createDirectory(temp.resolve("uploads"));
        manifest = temp.resolve("manifest.json");
        repository = mock(DocumentRepository.class);
        service = new PublicSourceService(repository, uploads.toString(), manifest.toString());
    }

    @Test void defaultDeniesAllWithoutLookingUpPrivateMetadata() {
        service = new PublicSourceService(repository, uploads.toString(), "");
        assertEquals("SOURCE_NOT_PUBLIC", assertThrows(PublicSourceService.SourceUnavailable.class, () -> service.describe(1)).code);
        verifyNoInteractions(repository);
    }

    @ParameterizedTest @ValueSource(strings = {"", "{}", "{not-json", "{\"schemaVersion\":1,\"documents\":[]}"})
    void missingOrMalformedManifestFailsClosed(String content) throws Exception {
        Files.writeString(manifest, content);
        assertThrows(PublicSourceService.SourceUnavailable.class, () -> service.describe(1));
        verifyNoInteractions(repository);
    }

    @ParameterizedTest @ValueSource(strings = {"pdf", "md", "txt"})
    void approvedEvidenceSupportsCompleteFiles(String extension) throws Exception {
        Document document = fixture(1, extension, extension.equals("pdf") ? "%PDF-1.7\ncomplete PDF fixture" : "第一段\n最后一段");
        approve(document);
        when(repository.findById(1L)).thenReturn(Optional.of(document));
        assertEquals("AVAILABLE", service.describe(1).availability());
        if (extension.equals("pdf")) assertTrue(service.pdf(1).exists());
        else assertEquals("第一段\n最后一段", service.text(1));
    }

    @Test void canonicalIsRejectedEvenIfMistakenlyApproved() throws Exception {
        Document document = fixture(1, "txt", "private canonical");
        document.setDocumentRole(DocumentRole.CANONICAL);
        approve(document);
        when(repository.findById(1L)).thenReturn(Optional.of(document));
        assertThrows(PublicSourceService.SourceUnavailable.class, () -> service.describe(1));
        assertThrows(PublicSourceService.SourceUnavailable.class, () -> service.text(1));
    }

    @Test void legacyEvidenceRoleWorksButNewUploadsAreNotAutomaticallyApproved() throws Exception {
        Document document = fixture(1, "txt", "legacy evidence");
        document.setDocumentRole(null);
        approve(document);
        when(repository.findById(1L)).thenReturn(Optional.of(document));
        assertEquals("AVAILABLE", service.describe(1).availability());
        assertThrows(PublicSourceService.SourceUnavailable.class, () -> service.describe(2));
        verify(repository, never()).findById(2L);
    }

    @Test void approvalRemovalAndFileReplacementImmediatelyRevokeAccess() throws Exception {
        Document document = fixture(1, "txt", "approved");
        approve(document);
        when(repository.findById(1L)).thenReturn(Optional.of(document));
        assertEquals("approved", service.text(1));
        Files.writeString(Path.of(document.getFilePath()), "replacement not reviewed");
        assertThrows(PublicSourceService.SourceUnavailable.class, () -> service.text(1));
        Files.writeString(manifest, "{\"schemaVersion\":1,\"documents\":[]}");
        assertThrows(PublicSourceService.SourceUnavailable.class, () -> service.describe(1));
    }

    @Test void missingFileDoesNotExposePathAndDoesNotBreakRag() throws Exception {
        Document document = fixture(1, "pdf", "%PDF-1.7");
        approve(document);
        document.setFilePath(uploads.resolve("missing.pdf").toString());
        when(repository.findById(1L)).thenReturn(Optional.of(document));
        var info = service.describe(1);
        assertEquals("FILE_MISSING", info.availability());
        assertFalse(new ObjectMapper().writeValueAsString(info).contains(uploads.toString()));
        assertEquals("FILE_MISSING", assertThrows(PublicSourceService.SourceUnavailable.class, () -> service.pdf(1)).code);
    }

    @Test void outsideUploadRootAndTraversalAreNeverRead() throws Exception {
        Document document = fixture(1, "txt", "approved");
        approve(document);
        Path outside = Files.writeString(temp.resolve("private.txt"), "secret fixture");
        document.setFilePath(uploads.resolve("../private.txt").toString());
        when(repository.findById(1L)).thenReturn(Optional.of(document));
        assertEquals("FILE_MISSING", service.describe(1).availability());
        assertThrows(PublicSourceService.SourceUnavailable.class, () -> service.text(1));
        assertEquals("secret fixture", Files.readString(outside));
    }

    @Test void symlinkOutsideUploadRootIsRejected() throws Exception {
        Path outside = Files.writeString(temp.resolve("private.txt"), "secret fixture");
        Path link = uploads.resolve("linked.txt");
        try { Files.createSymbolicLink(link, outside); }
        catch (java.io.IOException | UnsupportedOperationException exception) {
            org.junit.jupiter.api.Assumptions.abort("Host does not allow symlink creation");
        }
        Document document = fixture(1, "txt", "approved");
        approve(document);
        document.setFilePath(link.toString());
        when(repository.findById(1L)).thenReturn(Optional.of(document));
        assertEquals("FILE_MISSING", service.describe(1).availability());
        assertThrows(PublicSourceService.SourceUnavailable.class, () -> service.text(1));
    }

    @Test void docxIsDownloadableRatherThanPresentedAsConvertedPreview() throws Exception {
        Document document = fixture(1, "docx", "PK\u0003\u0004docx fixture");
        approve(document);
        when(repository.findById(1L)).thenReturn(Optional.of(document));
        assertEquals("DOWNLOADABLE", service.describe(1).availability());
        assertTrue(service.describe(1).message().contains("原始文件下载"));
        assertThrows(PublicSourceService.SourceUnavailable.class, () -> service.text(1));
        assertThrows(PublicSourceService.SourceUnavailable.class, () -> service.pdf(1));
    }

    @Test void approvedDocxCanBeDownloadedWithSafeOriginalName() throws Exception {
        Document document = fixture(8, "docx", "PK\u0003\u0004Test DOCX bytes");
        document.setOriginalFileName("C:\\private\\法学院 指南.docx");
        approve(document);
        when(repository.findById(8L)).thenReturn(Optional.of(document));
        var download = service.downloadDocx(8);
        assertEquals("法学院 指南.docx", download.fileName());
        assertTrue(download.resource().exists());
    }

    @Test void docxDownloadStillRequiresEvidenceRoleAndApprovedHash() throws Exception {
        Document document = fixture(8, "docx", "PK\u0003\u0004Test DOCX bytes");
        approve(document);
        document.setDocumentRole(DocumentRole.CANONICAL);
        when(repository.findById(8L)).thenReturn(Optional.of(document));
        assertThrows(PublicSourceService.SourceUnavailable.class, () -> service.downloadDocx(8));
    }

    @Test void docxDownloadIsDeniedByDefaultWithoutAnApprovalEntry() throws Exception {
        Document document = fixture(8, "docx", "PK\u0003\u0004private fixture");
        when(repository.findById(8L)).thenReturn(Optional.of(document));
        assertEquals("SOURCE_NOT_PUBLIC", assertThrows(PublicSourceService.SourceUnavailable.class,
                () -> service.downloadDocx(8)).code);
        verifyNoInteractions(repository);
    }

    @Test void docxDownloadRejectsChangedFileOutsideRootAndWrongSignature() throws Exception {
        Document changed = fixture(8, "docx", "PK\u0003\u0004approved");
        approve(changed);
        when(repository.findById(8L)).thenReturn(Optional.of(changed));
        Files.writeString(Path.of(changed.getFilePath()), "PK\u0003\u0004replacement");
        assertThrows(PublicSourceService.SourceUnavailable.class, () -> service.downloadDocx(8));

        Document forged = fixture(9, "docx", "not a zip docx");
        approve(forged);
        when(repository.findById(9L)).thenReturn(Optional.of(forged));
        assertEquals("INVALID_DOCX", assertThrows(PublicSourceService.SourceUnavailable.class,
                () -> service.downloadDocx(9)).code);
    }

    @Test void htmlMarkdownIsReturnedOnlyAsRawText() throws Exception {
        String content = "<script>alert(1)</script>\n![x](https://example.invalid/tracker)";
        Document document = fixture(1, "md", content);
        approve(document);
        when(repository.findById(1L)).thenReturn(Optional.of(document));
        assertEquals(content, service.text(1));
    }

    @Test void forgedPdfContentIsNotServedAsPdf() throws Exception {
        Document document = fixture(1, "pdf", "<html>not a PDF</html>");
        approve(document);
        when(repository.findById(1L)).thenReturn(Optional.of(document));
        assertEquals("INVALID_PDF", assertThrows(PublicSourceService.SourceUnavailable.class, () -> service.pdf(1)).code);
    }

    @Test void invalidUtf8FailsRatherThanSilentlyLosingOriginalText() throws Exception {
        Document document = fixture(1, "txt", "replace me");
        Files.write(Path.of(document.getFilePath()), new byte[]{(byte) 0xff});
        approve(document);
        when(repository.findById(1L)).thenReturn(Optional.of(document));
        assertEquals("TEXT_UNAVAILABLE", assertThrows(PublicSourceService.SourceUnavailable.class, () -> service.text(1)).code);
    }

    @Test void duplicateApprovalIdsAndMissingAuthorizationEvidenceFailClosed() throws Exception {
        Document document = fixture(1, "txt", "approved");
        var approval = approval(document);
        writeManifest(List.of(approval, approval));
        assertThrows(PublicSourceService.SourceUnavailable.class, () -> service.describe(1));
        writeManifest(List.of(new PublicSourceService.Approval(1, approval.sha256(), "APPROVED", "", "reviewer", "2026-01-01")));
        assertThrows(PublicSourceService.SourceUnavailable.class, () -> service.describe(1));
        verifyNoInteractions(repository);
    }

    private Document fixture(long id, String extension, String content) throws Exception {
        Path file = Files.writeString(uploads.resolve("fixture-" + id + "." + extension), content);
        Document document = new Document();
        document.setId(id); document.setTitle("Test fixture " + id); document.setYear(2026);
        document.setSourceType("PERSONAL"); document.setFilePath(file.toString());
        document.setOriginalFileName("fixture." + extension);
        return document;
    }
    private PublicSourceService.Approval approval(Document document) throws Exception {
        String sha = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(Path.of(document.getFilePath()))));
        return new PublicSourceService.Approval(document.getId(), sha, "APPROVED", "Test-only permission fixture", "test", "2026-01-01");
    }
    private void approve(Document document) throws Exception { writeManifest(List.of(approval(document))); }
    private void writeManifest(List<PublicSourceService.Approval> approvals) throws Exception {
        new ObjectMapper().writeValue(manifest.toFile(), new PublicSourceService.Manifest(1, approvals));
    }
}
