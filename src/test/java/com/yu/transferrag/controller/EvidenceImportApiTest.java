package com.yu.transferrag.controller;
import com.yu.transferrag.dto.DocumentImportResponse;
import com.yu.transferrag.service.DocumentImportService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import java.nio.file.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
class EvidenceImportApiTest {
    @Test void registeredProcessingResponseIs202NotSuccess201() {
        var service = mock(DocumentImportService.class);
        var controller = new DocumentImportController(service);
        var file = new MockMultipartFile("file","test.md","text/markdown",new byte[]{1});
        var response = new DocumentImportResponse(); response.setStatus("QUEUED"); response.setTaskId(1L);
        when(service.importDocument(file,null,"test",2026,"PERSONAL","DEPARTMENT")).thenReturn(response);
        assertEquals(202,controller.importDocument(file,null,"test",2026,"PERSONAL","DEPARTMENT").getStatusCode().value());
        response.setStatus("PROCESSING"); response.setDuplicate(true);
        assertEquals(202,controller.importDocument(file,null,"test",2026,"PERSONAL","DEPARTMENT").getStatusCode().value());
        response.setStatus("SUCCEEDED");
        assertEquals(200,controller.importDocument(file,null,"test",2026,"PERSONAL","DEPARTMENT").getStatusCode().value());
    }
    @Test void committedNginxTemplateDoesNotPubliclyProxyTaskApis() throws Exception {
        String nginx = Files.readString(Path.of("config/nginx/nju-transfer-rag-site.conf"));
        assertTrue(nginx.contains("location ^~ /api/ {\n        return 404;")
            || nginx.contains("location ^~ /api/ {\r\n        return 404;"));
        assertFalse(nginx.contains("location /api/import-tasks"));
        assertTrue(nginx.contains("location ^~ /admin/api/"));
        assertTrue(nginx.contains("deny all;")); assertTrue(nginx.contains("auth_basic_user_file"));
        // This checks the repository template only, not production Nginx execution.
    }
}
