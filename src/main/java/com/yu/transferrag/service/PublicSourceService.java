package com.yu.transferrag.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yu.transferrag.entity.Document;
import com.yu.transferrag.entity.DocumentRole;
import com.yu.transferrag.repository.DocumentRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Independent, read-only access. Never grants permission from sourceType or sourceUrl. */
@Service
public class PublicSourceService {
    private static final long MAX_FILE_BYTES = 50L * 1024 * 1024;
    private static final long MAX_TEXT_BYTES = 2L * 1024 * 1024;
    private final DocumentRepository documents;
    private final Path uploadRoot;
    private final String manifestPath;
    private final ObjectMapper json = new ObjectMapper();

    public PublicSourceService(DocumentRepository documents,
                               @Value("${app.upload-dir}") String uploadRoot,
                               @Value("${app.sources.public-manifest:}") String manifestPath) {
        this.documents = documents;
        this.uploadRoot = Path.of(uploadRoot).toAbsolutePath().normalize();
        this.manifestPath = manifestPath;
    }

    public SourceInfo describe(long id) {
        Document document = approvedDocument(id);
        Path path = confinedFile(document);
        String format = extension(document.getOriginalFileName());
        if (format.isEmpty() && path != null) format = extension(path.getFileName().toString());
        String availability;
        String message;
        if (path == null) {
            availability = "FILE_MISSING";
            message = "原始文件暂不可用，不影响已有问答。";
        } else if (!matchesApproval(document, path)) {
            throw unavailable();
        } else if (size(path) > MAX_FILE_BYTES || (Set.of("md", "txt").contains(format) && size(path) > MAX_TEXT_BYTES)) {
            availability = "TOO_LARGE";
            message = "文档超过当前安全预览大小限制，请联系维护者。";
        } else if ("docx".equals(format)) {
            availability = "DOWNLOADABLE";
            message = "DOCX 提供原始文件下载，不在网页中转换或预览。";
        } else if (Set.of("pdf", "md", "txt").contains(format)) {
            availability = "AVAILABLE";
            message = "";
        } else {
            availability = "UNSUPPORTED";
            message = "此格式暂不支持安全的完整原文预览。";
        }
        return new SourceInfo(id, document.getTitle(), document.getSourceType(), document.getYear(),
                format, availability, message);
    }

    public Resource pdf(long id) {
        SourceInfo info = requireAvailability(id, "AVAILABLE");
        if (!"pdf".equals(info.format())) throw new SourceUnavailable(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED", "此接口仅提供 PDF 原文。");
        // Resolve again: file requests must independently recheck permission and real path.
        Document document = approvedDocument(id);
        Path path = confinedFile(document);
        if (path == null) throw missing();
        if (!matchesApproval(document, path)) throw unavailable();
        try (var input = Files.newInputStream(path)) {
            if (!new String(input.readNBytes(5), StandardCharsets.US_ASCII).equals("%PDF-")) {
                throw new SourceUnavailable(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "INVALID_PDF", "该文件不是可识别的 PDF，请联系维护者。");
            }
        } catch (IOException exception) {
            throw missing();
        }
        return new FileSystemResource(path);
    }

    public String text(long id) {
        SourceInfo info = requireAvailability(id, "AVAILABLE");
        if (!Set.of("md", "txt").contains(info.format())) throw new SourceUnavailable(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED", "此接口仅提供 Markdown/TXT 只读文本。");
        Document document = approvedDocument(id);
        Path path = confinedFile(document);
        if (path == null) throw missing();
        if (!matchesApproval(document, path)) throw unavailable();
        try (var input = Files.newInputStream(path)) {
            byte[] bytes = input.readNBytes((int) MAX_TEXT_BYTES + 1);
            if (bytes.length > MAX_TEXT_BYTES) throw new SourceUnavailable(HttpStatus.PAYLOAD_TOO_LARGE, "TOO_LARGE", "文档超过当前安全预览大小限制。");
            return StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(bytes)).toString();
        } catch (IOException exception) {
            throw new SourceUnavailable(HttpStatus.UNPROCESSABLE_ENTITY, "TEXT_UNAVAILABLE", "文本无法按 UTF-8 完整读取，请联系维护者。");
        }
    }

    public OriginalDownload downloadDocx(long id) {
        SourceInfo info = requireAvailability(id, "DOWNLOADABLE");
        if (!"docx".equals(info.format())) {
            throw new SourceUnavailable(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED",
                    "此接口仅提供 DOCX 原始文件下载。");
        }
        Document document = approvedDocument(id);
        Path path = confinedFile(document);
        if (path == null) throw missing();
        if (!matchesApproval(document, path)) throw unavailable();
        try (var input = Files.newInputStream(path)) {
            byte[] signature = input.readNBytes(4);
            if (signature.length != 4 || signature[0] != 'P' || signature[1] != 'K'
                    || signature[2] != 3 || signature[3] != 4) {
                throw new SourceUnavailable(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "INVALID_DOCX",
                        "该文件不是可识别的 DOCX 原件。");
            }
        } catch (IOException exception) {
            throw missing();
        }
        return new OriginalDownload(new FileSystemResource(path), safeDownloadName(document, path));
    }

    private String safeDownloadName(Document document, Path path) {
        String original = document.getOriginalFileName();
        String basename = original == null || original.isBlank()
                ? "document-" + document.getId() + ".docx"
                : original.replace('\\', '/').substring(original.replace('\\', '/').lastIndexOf('/') + 1);
        StringBuilder safe = new StringBuilder(Math.min(basename.length(), 180));
        basename.codePoints().filter(codePoint -> !Character.isISOControl(codePoint))
                .limit(180).forEach(safe::appendCodePoint);
        String result = safe.toString().strip();
        if (result.isBlank() || !result.toLowerCase(Locale.ROOT).endsWith(".docx")) {
            return "document-" + document.getId() + ".docx";
        }
        return result;
    }

    private SourceInfo requireAvailability(long id, String expected) {
        SourceInfo info = describe(id);
        if (!expected.equals(info.availability())) {
            throw new SourceUnavailable("FILE_MISSING".equals(info.availability()) ? HttpStatus.NOT_FOUND : HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    info.availability(), info.message());
        }
        return info;
    }

    private Document approvedDocument(long id) {
        if (id <= 0 || approval(id) == null) throw unavailable();
        Document document = documents.findById(id).orElseThrow(PublicSourceService::unavailable);
        if (document.getDocumentRole() != DocumentRole.EVIDENCE) throw unavailable();
        return document;
    }

    /** Re-read on every request: removing an entry revokes access without restarting. */
    private Approval approval(long id) {
        if (manifestPath == null || manifestPath.isBlank()) return null;
        try {
            Path path = Path.of(manifestPath);
            if (!Files.isRegularFile(path) || Files.size(path) > 1024 * 1024) return null;
            Manifest manifest = json.readValue(Files.readAllBytes(path), Manifest.class);
            if (manifest.schemaVersion() != 1 || manifest.documents() == null) return null;
            Approval found = null;
            var ids = new java.util.HashSet<Long>();
            for (Approval entry : manifest.documents()) {
                if (entry == null || entry.documentId() <= 0 || !ids.add(entry.documentId())
                        || !"APPROVED".equals(entry.authorizationStatus())
                        || entry.sha256() == null || !entry.sha256().matches("[a-fA-F0-9]{64}")
                        || entry.authorizationNote() == null || entry.authorizationNote().isBlank()
                        || entry.reviewedBy() == null || entry.reviewedBy().isBlank()
                        || entry.reviewedAt() == null || LocalDate.parse(entry.reviewedAt()).isAfter(LocalDate.now())) return null;
                if (entry.documentId() == id) found = entry;
            }
            return found;
        } catch (IOException | RuntimeException exception) {
            // No raw parser message or config/file path is exposed or logged.
            return null;
        }
    }

    private boolean matchesApproval(Document document, Path path) {
        Approval approval = approval(document.getId());
        if (approval == null || size(path) > MAX_FILE_BYTES) return false;
        try (var input = Files.newInputStream(path)) {
            MessageDigest hash = MessageDigest.getInstance("SHA-256");
            byte[] buffer = new byte[8192];
            int count;
            long total = 0;
            while ((count = input.read(buffer)) != -1) {
                total += count;
                if (total > MAX_FILE_BYTES) return false;
                hash.update(buffer, 0, count);
            }
            return HexFormat.of().formatHex(hash.digest()).equalsIgnoreCase(approval.sha256());
        } catch (IOException | NoSuchAlgorithmException exception) {
            return false;
        }
    }

    private Path confinedFile(Document document) {
        try {
            if (document.getFilePath() == null || document.getFilePath().isBlank()) return null;
            Path root = uploadRoot.toRealPath();
            Path candidate = Path.of(document.getFilePath()).toAbsolutePath().normalize();
            if (!candidate.startsWith(uploadRoot)) return null;
            Path real = candidate.toRealPath();
            return real.startsWith(root) && !real.equals(root) && Files.isRegularFile(real) && Files.isReadable(real) ? real : null;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private static String extension(String name) {
        if (name == null) return "";
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static long size(Path path) {
        try { return Files.size(path); } catch (IOException exception) { return Long.MAX_VALUE; }
    }

    private static SourceUnavailable unavailable() {
        return new SourceUnavailable(HttpStatus.NOT_FOUND, "SOURCE_NOT_PUBLIC", "该原文尚未确认公开展示权限，或暂不可用。已有问答不受影响。");
    }

    private static SourceUnavailable missing() {
        return new SourceUnavailable(HttpStatus.NOT_FOUND, "FILE_MISSING", "原始文件暂不可用，不影响已有问答。");
    }

    public record Manifest(int schemaVersion, List<Approval> documents) {}
    public record Approval(long documentId, String sha256, String authorizationStatus,
                           String authorizationNote, String reviewedBy, String reviewedAt) {}
    public record SourceInfo(long documentId, String title, String sourceType, Integer documentYear,
                             String format, String availability, String message) {}
    public record OriginalDownload(Resource resource, String fileName) {}
    public static class SourceUnavailable extends RuntimeException {
        public final HttpStatus status;
        public final String code;
        public SourceUnavailable(HttpStatus status, String code, String message) {
            super(message);
            this.status = status;
            this.code = code;
        }
    }
}
