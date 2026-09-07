package com.yu.transferrag.controller;

import com.yu.transferrag.dto.AskRequest;
import com.yu.transferrag.dto.RagResponse;
import com.yu.transferrag.dto.SourceResponse;
import com.yu.transferrag.service.RagService;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RagControllerTest {

    @Test
    void shouldReturnAnswerAndSourcesFromAskEndpoint() {
        RagService ragService = mock(RagService.class);
        RagController controller = new RagController(ragService);
        AskRequest request = new AskRequest();
        request.setQuestion("2025年汉语言文学转专业需要什么条件？");

        SourceResponse source = new SourceResponse();
        source.setCitationId("S1");
        source.setDocumentId(6L);
        source.setChunkId(116L);

        RagResponse expected = new RagResponse();
        expected.setQuestion(request.getQuestion());
        expected.setAnswer("申请条件见官方准入计划。[S1]");
        expected.setSources(List.of(source));
        when(ragService.ask(request.getQuestion())).thenReturn(expected);

        ResponseEntity<RagResponse> response = controller.ask(request);

        assertEquals(200, response.getStatusCode().value());
        assertEquals(expected, response.getBody());
        assertEquals("S1", response.getBody().getSources().getFirst().getCitationId());
    }
}
