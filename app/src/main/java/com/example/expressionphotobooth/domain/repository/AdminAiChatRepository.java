package com.example.expressionphotobooth.domain.repository;

import com.example.expressionphotobooth.domain.model.AdminAiChatRequest;
import com.example.expressionphotobooth.domain.model.AdminAiChatResponse;

public interface AdminAiChatRepository {
    interface Callback {
        void onSuccess(AdminAiChatResponse response);
        void onError(String message);
    }

    void sendQuery(AdminAiChatRequest request, Callback callback);
}
