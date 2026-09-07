package com.yu.transferrag.dto;

import jakarta.validation.constraints.NotBlank;

public class AskRequest {

    @NotBlank
    private String question;

    public String getQuestion() {
        return question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }
}
