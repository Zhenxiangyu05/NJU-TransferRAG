package com.yu.transferrag.service;

import com.yu.transferrag.dto.AnswerabilityResult;
import com.yu.transferrag.dto.SourceResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AnswerabilityServiceTest {

    @Mock
    private ChatModel chatModel;

    private AnswerabilityService answerabilityService;

    @BeforeEach
    void setUp() {
        answerabilityService = new AnswerabilityService(chatModel);
    }

    @Test
    void shouldAcceptExplicitEvidenceThatWrittenExamIsRequired() {
        AnswerabilityResult result = checkWithModelOutput(
                "汉语言文学转专业要不要笔试？",
                "[S1]\ncontent:\n资格审核通过后，组织笔试和面试。",
                """
                        {"answerable":true,"evidenceCitationIds":["S1"],"reason":"S1 explicitly states that a written exam is organized."}
                        """
        );

        assertTrue(result.answerable());
        assertEquals(List.of("S1"), result.evidenceCitationIds());

        ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
        verify(chatModel).call(promptCaptor.capture());
        assertTrue(promptCaptor.getValue().getSystemMessage().getText().contains("不可信数据"));
        assertTrue(promptCaptor.getValue().getUserMessage().getText()
                .contains("<BEGIN_UNTRUSTED_SOURCES>"));
    }

    @Test
    void shouldRejectDateQuestionWhenSourceOnlyMentionsAnExam() {
        AnswerabilityResult result = checkWithModelOutput(
                "汉语言文学笔试具体是哪一天？",
                "[S1]\ncontent:\n资格审核通过后，组织笔试和面试。",
                """
                        {"answerable":false,"evidenceCitationIds":[],"reason":"The source gives no written-exam date."}
                        """
        );

        assertFalse(result.answerable());
        assertEquals(List.of(), result.evidenceCitationIds());
    }

    @Test
    void shouldRejectTimeQuestionWhenSourceOnlyMentionsAnExam() {
        AnswerabilityResult result = checkWithModelOutput(
                "汉语言文学笔试具体几点开始？",
                "[S1]\ncontent:\n资格审核通过后，组织笔试和面试。",
                """
                        {"answerable":false,"evidenceCitationIds":[],"reason":"The source gives no start time."}
                        """
        );

        assertFalse(result.answerable());
    }

    @Test
    void shouldAcceptEvidenceContainingRequiredCoursesAndCredits() {
        AnswerabilityResult result = checkWithModelOutput(
                "汉语言文学需要修哪些准入课程和多少学分？",
                "[S1]\ncontent:\n须修古代汉语（上）、古代汉语（下）、中国古代文学（一）、中国古代文学（二）中的至少三门，并取得9学分。",
                """
                        {"answerable":true,"evidenceCitationIds":["S1"],"reason":"S1 lists the courses and the required 9 credits."}
                        """
        );

        assertTrue(result.answerable());
        assertEquals(List.of("S1"), result.evidenceCitationIds());
    }

    @Test
    void shouldRejectMultiPartQuestionWhenOnePartLacksEvidence() {
        AnswerabilityResult result = checkWithModelOutput(
                "汉语言文学需要修什么课程，笔试是哪一天？",
                "[S1]\ncontent:\n须修四门准入课程中的至少三门并取得9学分。",
                """
                        {"answerable":false,"evidenceCitationIds":[],"reason":"The course requirement is present, but the exam date is absent."}
                        """
        );

        assertFalse(result.answerable());
    }

    @Test
    void shouldFailClosedWhenModelOutputCannotBeParsed() {
        AnswerabilityResult result = checkWithModelOutput(
                "要不要笔试？",
                "[S1]\ncontent:\n组织笔试。",
                "answerable, probably yes"
        );

        assertFalse(result.answerable());
        assertEquals(List.of(), result.evidenceCitationIds());
    }

    @Test
    void shouldFailClosedWhenAnswerableResultReferencesUnknownCitation() {
        AnswerabilityResult result = checkWithModelOutput(
                "要不要笔试？",
                "[S1]\ncontent:\n组织笔试。",
                """
                        {"answerable":true,"evidenceCitationIds":["S99"],"reason":"S99 supports the answer."}
                        """
        );

        assertFalse(result.answerable());
        assertEquals(List.of(), result.evidenceCitationIds());
    }

    @Test
    void shouldFailClosedWhenAnswerableFieldIsMissing() {
        AnswerabilityResult result = checkWithModelOutput(
                "要不要笔试？",
                "[S1]\ncontent:\n组织笔试。",
                """
                        {"evidenceCitationIds":["S1"],"reason":"The answerable field is missing."}
                        """
        );

        assertFalse(result.answerable());
        assertEquals(List.of(), result.evidenceCitationIds());
    }

    @Test
    void shouldFailClosedWhenAnswerableResultHasNoEvidenceCitation() {
        AnswerabilityResult result = checkWithModelOutput(
                "要不要笔试？",
                "[S1]\ncontent:\n组织笔试。",
                """
                        {"answerable":true,"evidenceCitationIds":[],"reason":"No citation supplied."}
                        """
        );

        assertFalse(result.answerable());
        assertEquals(List.of(), result.evidenceCitationIds());
    }

    @Test
    void shouldFailClosedWhenEvidenceCheckCallThrows() {
        when(chatModel.call(any(Prompt.class))).thenThrow(new IllegalStateException("API unavailable"));

        AnswerabilityResult result = answerabilityService.check(
                "要不要笔试？",
                "[S1]\ncontent:\n组织笔试。",
                List.of(source("S1"))
        );

        assertFalse(result.answerable());
        assertEquals(List.of(), result.evidenceCitationIds());
    }

    private AnswerabilityResult checkWithModelOutput(String question,
                                                     String context,
                                                     String modelOutput) {
        when(chatModel.call(any(Prompt.class))).thenReturn(chatResponse(modelOutput));
        return answerabilityService.check(
                question,
                context,
                List.of(source("S1"))
        );
    }

    private SourceResponse source(String citationId) {
        SourceResponse source = new SourceResponse();
        source.setCitationId(citationId);
        return source;
    }

    private ChatResponse chatResponse(String output) {
        return new ChatResponse(List.of(
                new Generation(new AssistantMessage(output))
        ));
    }
}
