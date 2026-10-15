package com.yu.transferrag.dto;

import java.util.List;

public class RagResponse {

    private String question;
    private String answer;
    private List<SourceResponse> sources;
    private String conversationToken;
    private Long expiresInSeconds;
    private String nextRequestId;

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public String getNextRequestId() { return nextRequestId; }
    public void setNextRequestId(String value) { nextRequestId = value; }

    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public String getConversationToken() { return conversationToken; }
    public void setConversationToken(String value) { conversationToken = value; }
    @com.fasterxml.jackson.annotation.JsonInclude(com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL)
    public Long getExpiresInSeconds() { return expiresInSeconds; }
    public void setExpiresInSeconds(Long value) { expiresInSeconds = value; }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public List<SourceResponse> getSources() {
        return sources;
    }

    public void setSources(List<SourceResponse> sources) {
        this.sources = sources;
    }
}
