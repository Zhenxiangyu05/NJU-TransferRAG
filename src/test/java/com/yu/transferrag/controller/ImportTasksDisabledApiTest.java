package com.yu.transferrag.controller;

import com.yu.transferrag.entity.*;
import com.yu.transferrag.exception.ImportTasksDisabledException;
import com.yu.transferrag.repository.ImportTaskRepository;
import com.yu.transferrag.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ImportTasksDisabledApiTest {
    @Test void retryAndImportHaveSameSafe503ContractAndPerformNoWrites() throws Exception {
        var tasks = mock(ImportTaskRepository.class);
        var documents = mock(DocumentService.class);
        var registration = mock(EvidenceRegistrationService.class);
        var flag = new ImportTaskFeatureGuard(false);
        var state = new ImportTaskStateService(tasks, 90, 3, flag);
        var imports = new DocumentImportService(documents, registration, state, flag);
        var mvc = MockMvcBuilders.standaloneSetup(new ImportTaskController(state), new DocumentImportController(imports))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/api/import-tasks/1/retry"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ImportTasksDisabledException.CODE))
                .andExpect(jsonPath("$.message").value(ImportTasksDisabledException.USER_MESSAGE));
        mvc.perform(multipart("/api/documents/import")
                        .file(new MockMultipartFile("file", "test.md", "text/markdown", new byte[]{1}))
                        .param("department", "test").param("year", "2026").param("sourceType", "PERSONAL"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value(ImportTasksDisabledException.CODE));
        verifyNoInteractions(tasks, documents, registration);
    }

    @Test void readOnlyTaskApiStillReturnsExistingStateWhenDisabled() throws Exception {
        var tasks = mock(ImportTaskRepository.class);
        var task = new ImportTask(); var document = new Document(); document.setId(2L); document.setTitle("fixture");
        task.setId(1L); task.setDocument(document); task.setStatus(ImportTaskStatus.FAILED);
        task.setStage(ImportTaskStage.INDEXING); task.setMaxAttempts(3);
        when(tasks.findById(1L)).thenReturn(Optional.of(task));
        var state = new ImportTaskStateService(tasks, 90, 3, new ImportTaskFeatureGuard(false));
        var mvc = MockMvcBuilders.standaloneSetup(new ImportTaskController(state)).build();
        mvc.perform(get("/api/import-tasks/1"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.status").value("FAILED"));
        verify(tasks, never()).lockById(anyLong());
    }
}
