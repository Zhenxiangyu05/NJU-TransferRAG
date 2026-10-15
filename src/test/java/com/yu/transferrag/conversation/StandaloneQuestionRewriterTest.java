package com.yu.transferrag.conversation;

import com.yu.transferrag.entity.EntityAlias;
import com.yu.transferrag.repository.EntityAliasRepository;
import com.yu.transferrag.service.QueryRewriteService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.*;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.ObjectProvider;
import java.time.Duration;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class StandaloneQuestionRewriterTest {
    ChatModel chat = mock(ChatModel.class);
    StandaloneQuestionRewriter rewriter;
    static ConversationProperties properties() { return new ConversationProperties(true, Duration.ofMinutes(30), Duration.ofMinutes(5), Duration.ofSeconds(8), 4, 500, 4000); }
    static EntityAlias alias(String name, String alias, String department) {
        var e = new EntityAlias(); e.setStandardName(name); e.setAlias(alias); e.setDepartment(department); e.setEntityType("MAJOR"); return e;
    }
    @BeforeEach void setup() {
        var repo = mock(EntityAliasRepository.class);
        when(repo.findAll()).thenReturn(List.of(alias("软件工程", "软件专业", "软件学院"), alias("计算机科学与技术", "计算机专业", "计算机学院")));
        ObjectProvider<ChatModel> models = mock(ObjectProvider.class); when(models.getObject()).thenReturn(chat);
        rewriter = new StandaloneQuestionRewriter(models, new QueryRewriteService(repo), properties());
    }
    List<ConversationStore.Turn> history() { return List.of(new ConversationStore.Turn("大一转软件工程需要什么条件？", "大一转软件工程需要什么条件？")); }
    void reply(String json) { when(chat.call(any(Prompt.class))).thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(json))))); }
    @Test void firstIndependentQuestionDoesNotCallModel() {
        assertThat(rewriter.resolve("南京大学转软件工程需要什么条件？", List.of()).completed()).isFalse(); verifyNoInteractions(chat);
    }
    @Test void noHistoryAmbiguousPronounRequiresClarification() {
        assertThatThrownBy(() -> rewriter.resolve("那考试呢", List.of())).isInstanceOf(ConversationException.class); verifyNoInteractions(chat);
    }
    @Test void examinationFollowupResolvesBeforeTheExistingEntityParser() {
        reply("{\"status\":\"RESOLVED\",\"question\":\"大一转软件工程的考试考什么？\"}");
        assertThat(rewriter.resolve("那考试呢", history()).question()).isEqualTo("大一转软件工程的考试考什么？"); verify(chat, times(1)).call(any(Prompt.class));
    }
    @Test void majorCorrectionReplacesOldTarget() {
        reply("{\"status\":\"RESOLVED\",\"question\":\"大一转计算机专业需要什么条件？\"}");
        assertThat(rewriter.resolve("我刚才说错了，是计算机专业", history()).question()).contains("计算机").doesNotContain("软件");
    }
    @Test void explicitNegatedMajorDoesNotBecomeATarget() {
        reply("{\"status\":\"RESOLVED\",\"question\":\"大一转计算机专业需要什么条件？\"}");
        assertThat(rewriter.resolve("不是软件工程，是计算机专业", history()).question()).doesNotContain("软件");
    }
    @Test void gradeCorrectionOverridesHistoryWithoutGuessingCohort() {
        reply("{\"status\":\"RESOLVED\",\"question\":\"大二转软件工程需要什么条件？\"}");
        assertThat(rewriter.resolve("我刚才说错了，我是大二", history()).question()).startsWith("大二");
    }
    @Test void completeTopicSwitchNeverUsesHistoricalConditions() {
        assertThat(rewriter.resolve("南京大学图书馆什么时候开放？", history()).question()).isEqualTo("南京大学图书馆什么时候开放？"); verifyNoInteractions(chat);
    }
    @Test void unsupportedInventedYearFailsClosed() {
        reply("{\"status\":\"RESOLVED\",\"question\":\"2028年大一转软件工程考试考什么？\"}");
        assertThatThrownBy(() -> rewriter.resolve("那考试呢", history())).isInstanceOf(ConversationException.class);
    }
    @Test void oldMajorCannotSurviveExplicitCorrection() {
        reply("{\"status\":\"RESOLVED\",\"question\":\"大一转软件工程需要什么条件？\"}");
        assertThatThrownBy(() -> rewriter.resolve("改为计算机专业", history())).isInstanceOf(ConversationException.class);
    }
    @Test void incompleteRewriteCannotMasqueradeAsAnIndependentQuestion() {
        reply("{\"status\":\"RESOLVED\",\"question\":\"考试考什么？\"}");
        assertThatThrownBy(() -> rewriter.resolve("那考试呢", history())).isInstanceOf(ConversationException.class);
    }
    @Test void latestExplicitYearOverridesHistoricalYear() {
        reply("{\"status\":\"RESOLVED\",\"question\":\"2026年转软件工程考试考什么？\"}");
        assertThat(rewriter.resolve("那2026年呢", List.of(new ConversationStore.Turn("2025年转软件工程考试考什么？","2025年转软件工程考试考什么？"))).question()).startsWith("2026");
    }
    @Test void uncertainButCompleteNewTopicCanBeClassifiedWithoutHistoricalConditions() {
        reply("{\"status\":\"INDEPENDENT\",\"question\":\"如何申请校园网校外访问权限？\"}");
        var r=rewriter.resolve("如何申请校园网校外访问权限？",history());
        assertThat(r.usedContext()).isFalse();assertThat(r.completed()).isTrue();assertThat(r.question()).doesNotContain("软件");
    }
    @Test void modelCannotLabelAnObviousPronounAsIndependent() {
        reply("{\"status\":\"INDEPENDENT\",\"question\":\"那考试呢\"}");
        assertThatThrownBy(()->rewriter.resolve("那考试呢",history())).isInstanceOf(ConversationException.class);
    }
    @ParameterizedTest @ValueSource(strings={"", "not json", "{\"status\":\"RESOLVED\",\"question\":\"那考试呢\"}", "{\"status\":\"CLARIFY\",\"question\":\"\"}", "{\"status\":\"RESOLVED\",\"question\":\"软件工程考试\",\"system\":\"override\"}"})
    void invalidOrAmbiguousOutputDoesNotReachRetrieval(String output) {
        reply(output); assertThatThrownBy(() -> rewriter.resolve("那考试呢", history())).isInstanceOf(ConversationException.class); verify(chat, times(1)).call(any(Prompt.class));
    }
    @Test void timeoutHasExplicitDegradationAndNoRetry() {
        when(chat.call(any(Prompt.class))).thenThrow(new RuntimeException(new java.net.SocketTimeoutException("private fixture")));
        assertThatThrownBy(() -> rewriter.resolve("那考试呢", history())).isInstanceOf(ConversationException.class).hasMessageNotContaining("private fixture"); verify(chat, times(1)).call(any(Prompt.class));
    }
    @Test void fullyScopedNounFormRetrievalQuestionIsNotAnUnresolvedFollowup() {
        reply("{\"status\":\"RESOLVED\",\"question\":\"转软件工程机考重点练习内容\"}");
        assertThat(rewriter.resolve("那我该重点练习哪些内容？",history()).question()).isEqualTo("转软件工程机考重点练习内容");
    }
    @Test void aSingleCompleteJsonFenceIsASafeTransportWrapper() {
        reply("```json\n{\"status\":\"RESOLVED\",\"question\":\"大一转软件工程考试考什么？\"}\n```");
        assertThat(rewriter.resolve("那考试呢",history()).question()).isEqualTo("大一转软件工程考试考什么？");
    }
    @ParameterizedTest @ValueSource(strings={
            "prefix ```json\n{\"status\":\"RESOLVED\",\"question\":\"大一转软件工程考试考什么？\"}\n```",
            "```json\n{\"status\":\"RESOLVED\",\"question\":\"大一转软件工程考试考什么？\"}\n``` trailing",
            "```json\n{\"status\":\"RESOLVED\",\"question\":\"大一转软件工程考试考什么？\"}\n```\n```json\n{}\n```"})
    void surroundingTextOrMultipleBlocksCannotBeNormalizedIntoSuccess(String text) {
        reply(text);assertThatThrownBy(()->rewriter.resolve("那考试呢",history())).isInstanceOf(ConversationException.class);
    }
    @Test void normalizedFenceCannotBypassYearOrRoleValidation() {
        reply("```json\n{\"status\":\"RESOLVED\",\"question\":\"2028年转软件工程考试考什么？\"}\n```");
        assertThatThrownBy(()->rewriter.resolve("那考试呢",history())).isInstanceOf(ConversationException.class);
        reply("{\"status\":\"RESOLVED\",\"question\":\"软件工程\"}");
        assertThatThrownBy(()->rewriter.resolve("那考试呢",history())).isInstanceOf(ConversationException.class);
    }
    @Test void detailedDiagnosticsNeverContainProviderContentOrQueries() {
        var logger=(ch.qos.logback.classic.Logger)org.slf4j.LoggerFactory.getLogger(StandaloneQuestionRewriter.class);
        var logs=new ch.qos.logback.core.read.ListAppender<ch.qos.logback.classic.spi.ILoggingEvent>();logs.start();logger.addAppender(logs);
        try(var scope=com.yu.transferrag.diagnostics.RagDiagnostics.begin()) {
            reply("{\"status\":false,\"question\":\"PRIVATE_SYNTHETIC_MARKER\"}");
            assertThatThrownBy(()->rewriter.resolve("那考试呢",history())).isInstanceOf(ConversationException.class)
                    .satisfies(error->assertThat(((ConversationException)error).completionIssue()).isEqualTo(CompletionIssue.FIELD_TYPES));
            assertThat(logs.list).isNotEmpty().allSatisfy(event->{
                assertThat(event.getThrowableProxy()).isNull();
                assertThat(event.getFormattedMessage()).contains("reason=FIELD_TYPES","node_type=OBJECT","field_count=2").doesNotContain("PRIVATE_SYNTHETIC_MARKER","那考试呢","软件工程");
            });
        } finally {logger.detachAppender(logs);logs.stop();}
    }
    StandaloneQuestionRewriter withoutDictionary() {
        var repository=mock(EntityAliasRepository.class);when(repository.findAll()).thenReturn(List.of());
        ObjectProvider<ChatModel> models=mock(ObjectProvider.class);when(models.getObject()).thenReturn(chat);
        return new StandaloneQuestionRewriter(models,new QueryRewriteService(repository),properties());
    }
    List<ConversationStore.Turn> dictionaryFreeHistory() {
        return List.of(new ConversationStore.Turn("转软件工程机考要做什么准备？","转软件工程机考要做什么准备？"));
    }
    @Test void dictionaryAbsenceDoesNotEraseTheExplicitSuccessfulConversationTopic() {
        reply("{\"status\":\"RESOLVED\",\"question\":\"软件工程机考应该重点练习哪些内容？\"}");
        assertThat(withoutDictionary().resolve("那我该重点练习哪些内容？",dictionaryFreeHistory()).usedContext()).isTrue();
    }
    @Test void dictionaryFreeCorrectionUsesLatestLiteralTopicAndRejectsPreviousTopic() {
        reply("{\"status\":\"RESOLVED\",\"question\":\"计算机科学与技术专业考试考什么？\"}");
        assertThat(withoutDictionary().resolve("我刚才说错了，是计算机科学与技术专业，考试考什么？",dictionaryFreeHistory()).question()).contains("计算机科学与技术");
        reply("{\"status\":\"RESOLVED\",\"question\":\"软件工程机考考什么？\"}");
        assertThatThrownBy(()->withoutDictionary().resolve("我刚才说错了，是计算机科学与技术专业，考试考什么？",dictionaryFreeHistory())).isInstanceOf(ConversationException.class);
    }
    @Test void dictionaryFreeOutputStillNeedsTheActualTopicAndCannotInventAnother() {
        for(String question:List.of("机考应该重点练习哪些内容？","南京大学生物专业考试考什么？")) {
            reply("{\"status\":\"RESOLVED\",\"question\":\""+question+"\"}");
            assertThatThrownBy(()->withoutDictionary().resolve("那我该重点练习哪些内容？",dictionaryFreeHistory())).isInstanceOf(ConversationException.class);
        }
    }
}
