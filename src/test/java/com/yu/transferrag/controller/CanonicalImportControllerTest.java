package com.yu.transferrag.controller;

import com.yu.transferrag.service.CanonicalImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class CanonicalImportControllerTest {

    @Mock
    private CanonicalImportService canonicalImportService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                new CanonicalImportController(canonicalImportService)
        ).build();
    }

    @Test
    void shouldRejectMalformedJsonWithoutCallingImportService() throws Exception {
        mockMvc.perform(post("/api/documents/canonical")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-valid-json"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(canonicalImportService);
    }
}
