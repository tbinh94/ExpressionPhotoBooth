package com.example.expressionphotobooth.domain.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class AdminAiInsightRequest {
    private final int rangeMonths;
    private final String languageTag;
    private final LinkedHashMap<String, Integer> usersByMonth;
    private final LinkedHashMap<String, Integer> imageDownloadsByMonth;
    private final LinkedHashMap<String, Double> reviewScoreByMonth;
    private final int aiRegisteredUsers;
    private final int aiNotRegisteredUsers;

    public AdminAiInsightRequest(
            int rangeMonths,
            String languageTag,
            Map<String, Integer> usersByMonth,
            Map<String, Integer> imageDownloadsByMonth,
            Map<String, Double> reviewScoreByMonth,
            int aiRegisteredUsers,
            int aiNotRegisteredUsers
    ) {
        this.rangeMonths = rangeMonths;
        this.languageTag = languageTag == null ? "en" : languageTag;
        this.usersByMonth = usersByMonth == null ? new LinkedHashMap<>() : new LinkedHashMap<>(usersByMonth);
        this.imageDownloadsByMonth = imageDownloadsByMonth == null ? new LinkedHashMap<>() : new LinkedHashMap<>(imageDownloadsByMonth);
        this.reviewScoreByMonth = reviewScoreByMonth == null ? new LinkedHashMap<>() : new LinkedHashMap<>(reviewScoreByMonth);
        this.aiRegisteredUsers = aiRegisteredUsers;
        this.aiNotRegisteredUsers = aiNotRegisteredUsers;
    }

    public int getRangeMonths() {
        return rangeMonths;
    }

    public String getLanguageTag() {
        return languageTag;
    }

    public LinkedHashMap<String, Integer> getUsersByMonth() {
        return new LinkedHashMap<>(usersByMonth);
    }

    public LinkedHashMap<String, Integer> getImageDownloadsByMonth() {
        return new LinkedHashMap<>(imageDownloadsByMonth);
    }

    public LinkedHashMap<String, Double> getReviewScoreByMonth() {
        return new LinkedHashMap<>(reviewScoreByMonth);
    }

    public int getAiRegisteredUsers() {
        return aiRegisteredUsers;
    }

    public int getAiNotRegisteredUsers() {
        return aiNotRegisteredUsers;
    }
}
