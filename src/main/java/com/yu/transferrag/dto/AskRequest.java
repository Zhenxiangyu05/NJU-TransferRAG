package com.yu.transferrag.dto;

import jakarta.validation.constraints.Size;

public class AskRequest {

    @Size(max = 1000)
    private String question;
    @Size(max = 43)
    private String conversationToken;
    @Size(max = 36)
    private String requestId;
    @Size(max = 24)
    private String action;

    public String getConversationToken() { return conversationToken; }
    public void setConversationToken(String value) { conversationToken = value; }
    public String getRequestId() { return requestId; }
    public void setRequestId(String value) { requestId = value; }
    public String getAction() { return action; }
    public void setAction(String value) { action = value; }
    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void rejectHistoryOrRole(String name, Object value) {
        throw new IllegalArgumentException("Unsupported ask field");
    }

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
