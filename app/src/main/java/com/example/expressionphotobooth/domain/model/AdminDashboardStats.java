package com.example.expressionphotobooth.domain.model;

import java.util.LinkedHashMap;
import java.util.Map;

public class AdminDashboardStats {
    private final int totalAccounts;
    private final int totalReviews;
    private final double averageRating;
    private final int lowRatingCount;
    private final int fiveStarCount;
    private final int imageDownloads;
    private final int videoDownloads;
    private final int aiRegisteredUsers;
    private final int aiNotRegisteredUsers;
    private final double aiRegisteredPercent;
    private final long lastReviewAtMillis;
    private final int[] ratingCounts;
    private final LinkedHashMap<String, Integer> usersByMonth;
    private final LinkedHashMap<String, Integer> imageDownloadsByMonth;
    private final LinkedHashMap<String, Double> reviewScoreByMonth;

    public AdminDashboardStats(
            int totalAccounts,
            int totalReviews,
            double averageRating,
            int lowRatingCount,
            int fiveStarCount,
            int imageDownloads,
            int videoDownloads,
            int aiRegisteredUsers,
            int aiNotRegisteredUsers,
            double aiRegisteredPercent,
            long lastReviewAtMillis,
            int[] ratingCounts,
            Map<String, Integer> usersByMonth,
            Map<String, Integer> imageDownloadsByMonth,
            Map<String, Double> reviewScoreByMonth
    ) {
        this.totalAccounts = totalAccounts;
        this.totalReviews = totalReviews;
        this.averageRating = averageRating;
        this.lowRatingCount = lowRatingCount;
        this.fiveStarCount = fiveStarCount;
        this.imageDownloads = imageDownloads;
        this.videoDownloads = videoDownloads;
        this.aiRegisteredUsers = aiRegisteredUsers;
        this.aiNotRegisteredUsers = aiNotRegisteredUsers;
        this.aiRegisteredPercent = aiRegisteredPercent;
        this.lastReviewAtMillis = lastReviewAtMillis;
        this.ratingCounts = ratingCounts == null ? new int[6] : ratingCounts.clone();
        this.usersByMonth = usersByMonth == null ? new LinkedHashMap<>() : new LinkedHashMap<>(usersByMonth);
        this.imageDownloadsByMonth = imageDownloadsByMonth == null ? new LinkedHashMap<>() : new LinkedHashMap<>(imageDownloadsByMonth);
        this.reviewScoreByMonth = reviewScoreByMonth == null ? new LinkedHashMap<>() : new LinkedHashMap<>(reviewScoreByMonth);
    }

    public int getTotalAccounts() {
        return totalAccounts;
    }

    public int getTotalReviews() {
        return totalReviews;
    }

    public double getAverageRating() {
        return averageRating;
    }

    public int getLowRatingCount() {
        return lowRatingCount;
    }

    public int getFiveStarCount() {
        return fiveStarCount;
    }

    public int getImageDownloads() {
        return imageDownloads;
    }

    public int getVideoDownloads() {
        return videoDownloads;
    }

    public int getAiRegisteredUsers() {
        return aiRegisteredUsers;
    }

    public int getAiNotRegisteredUsers() {
        return aiNotRegisteredUsers;
    }

    public double getAiRegisteredPercent() {
        return aiRegisteredPercent;
    }

    public long getLastReviewAtMillis() {
        return lastReviewAtMillis;
    }

    public int[] getRatingCounts() {
        return ratingCounts.clone();
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
}

