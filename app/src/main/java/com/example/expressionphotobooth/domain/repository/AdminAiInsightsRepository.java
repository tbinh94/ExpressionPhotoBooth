package com.example.expressionphotobooth.domain.repository;

import com.example.expressionphotobooth.domain.model.AdminAiInsightRequest;
import com.example.expressionphotobooth.domain.model.AdminAiInsights;

public interface AdminAiInsightsRepository {
    interface Callback {
        void onSuccess(AdminAiInsights insights);

        void onError(String message);
    }

    void fetchInsights(AdminAiInsightRequest request, Callback callback);
}
