package com.example.expressionphotobooth.domain.model;

public class AdminAiChatResponse {
    private final String answer;
    private final String model;

    public AdminAiChatResponse(String answer, String model) {
        this.answer = answer;
        this.model = model;
    }

    public String getAnswer() {
        return answer;
    }

    public String getModel() {
        return model;
    }
}
