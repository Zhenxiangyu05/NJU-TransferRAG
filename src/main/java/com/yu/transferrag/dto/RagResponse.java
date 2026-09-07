package com.yu.transferrag.dto;

import java.util.List;

public class RagResponse {

    private String question;
    private String answer;
    private List<SourceResponse> sources;

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
