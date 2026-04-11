package com.example.expressionphotobooth.domain.model;

import java.util.Map;

public class AdminAiChatRequest {
    private final String query;
    private final String languageTag;
    private final AdminDashboardStats statsSnapshot;

    public AdminAiChatRequest(String query, String languageTag, AdminDashboardStats statsSnapshot) {
        this.query = query;
        this.languageTag = languageTag;
        this.statsSnapshot = statsSnapshot;
    }

    public String getQuery() {
        return query;
    }

    public String getLanguageTag() {
        return languageTag;
    }

    public AdminDashboardStats getStatsSnapshot() {
        return statsSnapshot;
    }
}
