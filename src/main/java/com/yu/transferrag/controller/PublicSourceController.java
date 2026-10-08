package com.yu.transferrag.controller;

import com.yu.transferrag.service.PublicSourceService;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import java.nio.charset.StandardCharsets;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** No list, upload or write methods; separate from all existing admin endpoints. */
@RestController
@RequestMapping("/api/public/sources")
public class PublicSourceController {
    private final PublicSourceService sources;

    public PublicSourceController(PublicSourceService sources) { this.sources = sources; }

    @GetMapping("/{id}")
    public ResponseEntity<PublicSourceService.SourceInfo> describe(@PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff").body(sources.describe(id));
    }

    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> pdf(@PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"source-" + id + ".pdf\"")
                .header("X-Content-Type-Options", "nosniff")
                .header("Content-Security-Policy", "sandbox; default-src 'none'; frame-ancestors 'self'")
                .header("Referrer-Policy", "no-referrer")
                .body(sources.pdf(id));
    }

    @GetMapping("/{id}/download")
    public ResponseEntity<Resource> downloadDocx(@PathVariable long id) {
        PublicSourceService.OriginalDownload download = sources.downloadDocx(id);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(download.fileName(), StandardCharsets.UTF_8).build().toString())
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "no-referrer")
                .body(download.resource());
    }

    @GetMapping("/{id}/text")
    public ResponseEntity<Map<String, String>> text(@PathVariable long id) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("X-Content-Type-Options", "nosniff").body(Map.of("text", sources.text(id)));
    }

    @ExceptionHandler(PublicSourceService.SourceUnavailable.class)
    public ResponseEntity<Map<String, String>> unavailable(PublicSourceService.SourceUnavailable error) {
        return ResponseEntity.status(error.status).cacheControl(CacheControl.noStore())
                .body(Map.of("code", error.code, "message", error.getMessage()));
    }
}
