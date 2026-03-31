package com.example.expressionphotobooth.domain.repository;

import com.example.expressionphotobooth.domain.model.AdminDashboardStats;
import com.example.expressionphotobooth.domain.model.DownloadType;

public interface AdminStatsRepository {
    interface StatsCallback {
        void onSuccess(AdminDashboardStats stats);

        void onError(String message);
    }

    void fetchDashboardStats(StatsCallback callback);

    void recordDownload(DownloadType downloadType);

    void recordReviewSubmitted();
}


