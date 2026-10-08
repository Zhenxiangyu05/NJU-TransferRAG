package com.yu.transferrag.controller;

import com.yu.transferrag.service.PublicSourceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class PublicSourceControllerTest {
    @TempDir Path temp;
    PublicSourceService service;
    MockMvc mvc;
    @BeforeEach void setup() {
        service = mock(PublicSourceService.class);
        mvc = MockMvcBuilders.standaloneSetup(new PublicSourceController(service)).build();
    }
    @Test void metadataExposesOnlyPublicFields() throws Exception {
        when(service.describe(2)).thenReturn(new PublicSourceService.SourceInfo(2,"Fixture","PERSONAL",2026,"md","AVAILABLE",""));
        mvc.perform(get("/api/public/sources/2"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.documentId").value(2))
                .andExpect(jsonPath("$.filePath").doesNotExist()).andExpect(jsonPath("$.originalFileName").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store"));
    }
    @Test void pdfSupportsInlineAndByteRangeForBrowserReader() throws Exception {
        Path pdf = Files.writeString(temp.resolve("fixture.pdf"), "%PDF-1.7\nfixture contents");
        when(service.pdf(2)).thenReturn(new FileSystemResource(pdf));
        mvc.perform(get("/api/public/sources/2/file").header("Range", "bytes=0-4"))
                .andExpect(status().isPartialContent()).andExpect(content().string("%PDF-"))
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("Content-Disposition", "inline; filename=\"source-2.pdf\""));
    }
    @Test void docxDownloadUsesAttachmentAndCorrectMimeType() throws Exception {
        Path docx = Files.writeString(temp.resolve("fixture.docx"), "PK\u0003\u0004fixture");
        when(service.downloadDocx(8)).thenReturn(new PublicSourceService.OriginalDownload(
                new FileSystemResource(docx), "法学院 指南.docx"));
        mvc.perform(get("/api/public/sources/8/download"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.allOf(
                        org.hamcrest.Matchers.containsString("attachment"),
                        org.hamcrest.Matchers.containsString("filename*=UTF-8''"))))
                .andExpect(content().string("PK\u0003\u0004fixture"));
        verify(service).downloadDocx(8);
    }
    @Test void textIsJsonNotExecutableHtml() throws Exception {
        when(service.text(2)).thenReturn("<script>alert(1)</script>");
        mvc.perform(get("/api/public/sources/2/text"))
                .andExpect(status().isOk()).andExpect(content().contentTypeCompatibleWith("application/json"))
                .andExpect(jsonPath("$.text").value("<script>alert(1)</script>"));
    }
    @Test void accessDenialMapsToSafeNotFoundOnEveryEndpoint() throws Exception {
        var denied = new PublicSourceService.SourceUnavailable(HttpStatus.NOT_FOUND, "SOURCE_NOT_PUBLIC", "未确认公开权限");
        when(service.describe(2)).thenThrow(denied); when(service.pdf(2)).thenThrow(denied); when(service.text(2)).thenThrow(denied); when(service.downloadDocx(2)).thenThrow(denied);
        for (String suffix : new String[]{"", "/file", "/text", "/download"}) {
            mvc.perform(get("/api/public/sources/2" + suffix)).andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("SOURCE_NOT_PUBLIC"));
        }
    }
    @Test void noPublicUploadListOrWriteApi() throws Exception {
        mvc.perform(post("/api/public/sources/2")).andExpect(status().isMethodNotAllowed());
        mvc.perform(delete("/api/public/sources/2")).andExpect(status().isMethodNotAllowed());
        mvc.perform(get("/api/public/sources")).andExpect(status().isNotFound());
        verifyNoInteractions(service);
    }
    @Test void clientCannotSupplyAFilePath() throws Exception {
        when(service.describe(2)).thenReturn(new PublicSourceService.SourceInfo(2,"Fixture","PERSONAL",2026,"pdf","AVAILABLE",""));
        mvc.perform(get("/api/public/sources/2").param("path", "/etc/passwd"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.filePath").doesNotExist());
        verify(service).describe(2);
        verifyNoMoreInteractions(service);
    }
}
